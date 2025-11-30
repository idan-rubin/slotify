let busySlots = {};
let allParticipants = [];
let blackouts = [];
const START_HOUR = 7, END_HOUR = 19;

// Load state on page load
document.addEventListener('DOMContentLoaded', loadState);

async function loadState() {
    try {
        const res = await fetch('/api/state');
        const data = await res.json();
        if (data.hasData) {
            handleUploadComplete(data);
        }
    } catch (e) {
        console.error('Failed to load state:', e);
    }
}

async function clearCache() {
    if (!confirm('Clear all calendar data?')) return;
    try {
        await fetch('/api/state', { method: 'DELETE' });
        busySlots = {};
        allParticipants = [];
        document.getElementById('timeline-card').style.display = 'none';
        document.getElementById('settings-card').style.display = 'none';
        document.getElementById('config').style.display = 'none';
        document.getElementById('results').innerHTML = '';
        document.getElementById('clear-btn').style.display = 'none';
    } catch (e) {
        alert('Failed to clear cache: ' + e.message);
    }
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// Buffer toggle
let savedBuffer = 10;
function toggleBuffer() {
    const noBuffer = document.getElementById('no-buffer').checked;
    const bufferInput = document.getElementById('buffer');
    if (noBuffer) {
        savedBuffer = bufferInput.value;
        bufferInput.value = 0;
    } else {
        bufferInput.value = savedBuffer;
    }
}

// Blackout management
function addBlackout() {
    const start = document.getElementById('blackout-start').value;
    const end = document.getElementById('blackout-end').value;
    if (!start || !end) return alert('Please select both start and end times');
    if (start >= end) return alert('End time must be after start time');
    if (blackouts.some(b => b.start === start && b.end === end)) {
        return alert('This blocked time already exists');
    }
    blackouts.push({ start, end });
    renderBlackouts();
}

function removeBlackout(index) {
    blackouts.splice(index, 1);
    renderBlackouts();
}

function renderBlackouts() {
    const list = document.getElementById('blackout-list');
    if (blackouts.length === 0) {
        list.innerHTML = '<span style="color:#888;font-size:13px">No blocked times configured</span>';
        return;
    }
    list.innerHTML = blackouts.map((b, i) => `
        <span class="blackout-chip">
            ${escapeHtml(b.start)} - ${escapeHtml(b.end)}
            <button type="button" onclick="removeBlackout(${i})" aria-label="Remove blocked time">&times;</button>
        </span>
    `).join('');
}

// Participant management
function renderParticipantLists() {
    const requiredList = document.getElementById('required-list');
    const optionalList = document.getElementById('optional-list');

    requiredList.innerHTML = allParticipants.map(p => `
        <label class="participant-item">
            <input type="checkbox" name="required" value="${escapeHtml(p)}" data-participant="${escapeHtml(p)}" aria-label="Required: ${escapeHtml(p)}">
            <span>${escapeHtml(p)}</span>
        </label>
    `).join('');

    optionalList.innerHTML = allParticipants.map(p => `
        <label class="participant-item">
            <input type="checkbox" name="optional" value="${escapeHtml(p)}" data-participant="${escapeHtml(p)}" aria-label="Optional: ${escapeHtml(p)}">
            <span>${escapeHtml(p)}</span>
        </label>
    `).join('');

    requiredList.querySelectorAll('input').forEach(cb => {
        cb.addEventListener('change', () => toggleParticipant(cb.value, 'required'));
    });
    optionalList.querySelectorAll('input').forEach(cb => {
        cb.addEventListener('change', () => toggleParticipant(cb.value, 'optional'));
    });
}

function toggleParticipant(name, type) {
    const requiredCheckbox = document.querySelector(`#required-list input[data-participant="${CSS.escape(name)}"]`);
    const optionalCheckbox = document.querySelector(`#optional-list input[data-participant="${CSS.escape(name)}"]`);

    if (type === 'required' && requiredCheckbox.checked) {
        optionalCheckbox.checked = false;
        optionalCheckbox.parentElement.classList.remove('optional');
        requiredCheckbox.parentElement.classList.add('required');
    } else if (type === 'optional' && optionalCheckbox.checked) {
        requiredCheckbox.checked = false;
        requiredCheckbox.parentElement.classList.remove('required');
        optionalCheckbox.parentElement.classList.add('optional');
    } else {
        requiredCheckbox.parentElement.classList.remove('required');
        optionalCheckbox.parentElement.classList.remove('optional');
    }
    updateTimeline();
}

// Timeline rendering
function updateTimeline() {
    const required = [...document.querySelectorAll('#required-list input:checked')].map(c => c.value);
    const optional = [...document.querySelectorAll('#optional-list input:checked')].map(c => c.value);
    renderTimeline(allParticipants, required, optional);
}

function renderTimeline(participants, required = [], optional = []) {
    const hours = Array.from({length: END_HOUR - START_HOUR + 1}, (_, i) => String(START_HOUR + i).padStart(2, '0'));
    let html = '<div class="timeline">';
    html += '<div class="timeline-header">' + hours.map(h => `<span>${h}</span>`).join('') + '</div>';

    participants.forEach(name => {
        const slots = busySlots[name] || [];
        const safeName = escapeHtml(name);
        const isRequired = required.includes(name);
        const isOptional = optional.includes(name);
        const nameClass = isRequired ? 'required' : (isOptional ? 'optional' : '');

        html += `<div class="person-row">
            <div class="person-name ${nameClass}" title="${safeName}">${safeName}</div>
            <div class="day-bar">`;
        slots.forEach(slot => {
            const left = timeToPercent(slot.start);
            const width = timeToPercent(slot.end) - left;
            html += `<div class="busy-block" style="left:${left}%;width:${width}%"></div>`;
        });
        html += '</div></div>';
    });

    html += '</div>';
    html += '<div class="legend">';
    html += '<div class="legend-item"><div class="legend-box" style="background:linear-gradient(to right, #d1fae5, #a7f3d0)"></div>Free</div>';
    html += '<div class="legend-item"><div class="legend-box" style="background:#f87171"></div>Busy</div>';
    html += '<div class="legend-item"><div class="legend-box" style="background:#10b981"></div>Available</div>';
    html += '</div>';
    document.getElementById('timeline').innerHTML = html;
}

function timeToPercent(timeStr) {
    const [h, m] = timeStr.split(':').map(Number);
    const totalMinutes = (h - START_HOUR) * 60 + m;
    const dayMinutes = (END_HOUR - START_HOUR) * 60;
    return Math.max(0, Math.min(100, (totalMinutes / dayMinutes) * 100));
}

function highlightSlots(slots) {
    document.querySelectorAll('.available-block').forEach(el => el.remove());
    slots.forEach(slot => {
        document.querySelectorAll('.day-bar').forEach(bar => {
            const left = timeToPercent(slot.start);
            const width = timeToPercent(slot.end) - left;
            const block = document.createElement('div');
            block.className = 'available-block';
            block.style.cssText = `left:${left}%;width:${width}%`;
            bar.appendChild(block);
        });
    });
}

// File upload with SSE progress
async function upload() {
    const file = document.getElementById('file').files[0];
    if (!file) return alert('Select a file first');

    const form = new FormData();
    form.append('file', file);
    const uploadBtn = document.querySelector('button[onclick="upload()"]');
    const originalText = uploadBtn.textContent;

    uploadBtn.disabled = true;
    uploadBtn.textContent = 'Uploading...';

    try {
        const res = await fetch('/api/upload', { method: 'POST', body: form });

        // Validation errors return JSON, not SSE
        const contentType = res.headers.get('content-type') || '';
        if (!res.ok || contentType.includes('application/json')) {
            const err = await res.json();
            throw new Error(err.error || `Server error: ${res.status}`);
        }

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const events = buffer.split('\n\n');
            buffer = events.pop();

            for (const event of events) {
                const lines = event.split('\n');
                let eventType = '';
                let eventData = '';

                for (const line of lines) {
                    if (line.startsWith('event: ')) eventType = line.slice(7);
                    if (line.startsWith('data: ')) eventData = line.slice(6);
                }

                if (eventType === 'progress') {
                    uploadBtn.textContent = JSON.parse(eventData).message;
                } else if (eventType === 'done') {
                    handleUploadComplete(JSON.parse(eventData));
                } else if (eventType === 'error') {
                    throw new Error(JSON.parse(eventData).error);
                }
            }
        }
    } catch (e) {
        alert('Upload failed: ' + e.message);
    } finally {
        uploadBtn.disabled = false;
        uploadBtn.textContent = originalText;
    }
}

function handleUploadComplete(data) {
    busySlots = data.busySlots;
    allParticipants = data.participants;
    document.getElementById('participant-count').textContent = `${allParticipants.length} participants`;
    document.getElementById('timeline-card').style.display = 'block';
    document.getElementById('settings-card').style.display = 'block';
    document.getElementById('config').style.display = 'block';
    document.getElementById('clear-btn').style.display = 'inline-block';
    document.getElementById('results').innerHTML = '';
    renderBlackouts();
    renderParticipantLists();
    renderTimeline(data.participants);
}

// Find available slots
async function findSlots() {
    const required = [...document.querySelectorAll('#required-list input:checked')].map(c => c.value);
    const optional = [...document.querySelectorAll('#optional-list input:checked')].map(c => c.value);

    if (required.length < 2) {
        return alert('Select at least 2 required participants');
    }

    try {
        const res = await fetch('/api/meeting-request', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                required,
                optional,
                durationMinutes: +document.getElementById('duration').value,
                bufferMinutes: +document.getElementById('buffer').value,
                blackouts
            })
        });
        if (!res.ok) throw new Error(`Server error: ${res.status}`);
        const data = await res.json();

        if (data.slots.length) {
            let html = `<div class="slots-header">Found ${data.slots.length} available slot(s) for ${required.length} required participant(s):</div>`;
            data.slots.forEach(slot => {
                html += `<div class="slot-card">
                    <div class="slot-time">${escapeHtml(slot.start)}</div>
                    <div class="slot-attendees">`;
                if (slot.availableOptional.length > 0) {
                    html += `<span class="available">✓ ${slot.availableOptional.map(escapeHtml).join(', ')}</span>`;
                }
                if (slot.unavailableOptional.length > 0) {
                    html += ` <span class="unavailable">✗ ${slot.unavailableOptional.map(escapeHtml).join(', ')}</span>`;
                }
                html += `</div></div>`;
            });
            document.getElementById('results').innerHTML = html;
        } else {
            document.getElementById('results').innerHTML = '<p class="no-slots">No available slots found for all required participants</p>';
        }
        highlightSlots(data.slots);
    } catch (e) {
        document.getElementById('results').innerHTML = '<p class="error">Request failed: ' + escapeHtml(e.message) + '</p>';
    }
}

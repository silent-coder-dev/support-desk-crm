// Agent queue page: running timers, sorting and quick search
document.addEventListener('DOMContentLoaded', () => {
    const tbody = document.getElementById('ticketsBody');
    const rows = () => Array.from(tbody.querySelectorAll('tr[data-timestamp]'));
    const time = row => new Date(row.dataset.timestamp).getTime();

    // 1. Time since each in-progress ticket was claimed
    const pad = n => String(n).padStart(2, '0');
    function updateTimers() {
        document.querySelectorAll('.live-timer').forEach(el => {
            const seconds = Math.max(0, Math.floor((Date.now() - new Date(el.dataset.claimedAt).getTime()) / 1000));
            el.querySelector('span').textContent =
                `${pad(Math.floor(seconds / 3600))}:${pad(Math.floor((seconds % 3600) / 60))}:${pad(seconds % 60)}`;
        });
    }
    setInterval(updateTimers, 1000);
    updateTimers();

    // 2. Sorting. The choice is remembered because the page reloads on every live update.
    const statusRank = { OPEN: 1, IN_PROGRESS: 2, RESOLVED: 3, CLOSED: 3 };
    const sorters = {
        oldest: (a, b) => time(a) - time(b),
        newest: (a, b) => time(b) - time(a),
        urgent: (a, b) => (statusRank[a.dataset.status] - statusRank[b.dataset.status]) || (time(a) - time(b))
    };
    const buttons = document.querySelectorAll('.sort-btn');

    function applySort(name) {
        rows().sort(sorters[name]).forEach(row => tbody.appendChild(row));
        buttons.forEach(btn => {
            const isActive = btn.dataset.sort === name;
            btn.classList.toggle('active', isActive);
            btn.setAttribute('aria-pressed', isActive);
        });
        try { sessionStorage.setItem('queueSort', name); } catch (e) { /* private mode: just don't remember */ }
    }
    buttons.forEach(btn => btn.addEventListener('click', () => applySort(btn.dataset.sort)));

    let savedSort = null;
    try { savedSort = sessionStorage.getItem('queueSort'); } catch (e) { /* private mode */ }
    applySort(sorters[savedSort] ? savedSort : 'oldest');

    // 3. Quick search within the rows already on screen
    document.getElementById('liveSearchInput').addEventListener('input', event => {
        const term = event.target.value.toLowerCase().trim();
        rows().forEach(row => {
            row.style.display = row.textContent.toLowerCase().includes(term) ? '' : 'none';
        });
    });
});

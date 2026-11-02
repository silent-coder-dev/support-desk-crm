// Behaviour shared by every page
document.addEventListener('DOMContentLoaded', () => {
    // Show any message the server sent with this page
    document.querySelectorAll('.toast').forEach(el => new bootstrap.Toast(el, { delay: 4500 }).show());

    // Pages with data-live-stream refresh by themselves when a ticket changes
    const streamUrl = document.body.dataset.liveStream;
    if (!streamUrl || !window.EventSource) return;

    const liveStatus = document.getElementById('liveStatus');
    const setStatus = (text, isLive) => {
        if (!liveStatus) return;
        liveStatus.textContent = text;
        liveStatus.classList.toggle('is-live', isLive);
    };

    const source = new EventSource(streamUrl);
    source.onopen = () => setStatus('Live', true);
    source.onerror = () => setStatus('Reconnecting', false);
    source.addEventListener('TICKET_UPDATED', () => {
        // Never wipe out something the agent is typing
        const typing = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName);
        if (typing) {
            setStatus('New updates, refresh to see them', true);
        } else {
            window.location.reload();
        }
    });
});

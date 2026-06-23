const vscode = acquireVsCodeApi();
let rawPomText = '';
let latestPomData = null;

// Track which tabs have been rendered (Overview renders on load)
const renderedTabs = new Set();

// ---- Lazy render dispatcher ----
function ensureTabRendered(tabName) {
    if (renderedTabs.has(tabName)) { return; }
    renderedTabs.add(tabName);
    switch (tabName) {
        case 'overview':
            renderOverview(latestPomData);
            break;
        case 'hierarchy':
            renderDependencyHierarchy(latestPomData);
            break;
        case 'effective':
            renderEffectivePom(rawPomText);
            break;
        case 'raw':
            renderRawPom(rawPomText);
            break;
    }
}

// ---- Tab switching ----
document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        const tabName = btn.dataset.page;
        const pageId = 'page-' + tabName;
        // Lazy render on first click
        ensureTabRendered(tabName);
        // Deactivate all
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-page').forEach(p => p.classList.remove('active'));
        // Activate clicked
        btn.classList.add('active');
        document.getElementById(pageId).classList.add('active');
    });
});

// ---- Messages from extension ----
window.addEventListener('message', e => {
    const msg = e.data;
    switch (msg.type) {
        case 'setPomData':
            rawPomText = msg.rawText || '';
            latestPomData = msg.pomData;
            // First data arrival: render the default active tab (Overview)
            if (renderedTabs.size === 0) {
                ensureTabRendered('overview');
            } else {
                // Re-render only tabs that have already been visited
                renderedTabs.forEach(tabName => {
                    switch (tabName) {
                        case 'overview': renderOverview(latestPomData); break;
                        case 'hierarchy': renderDependencyHierarchy(latestPomData); break;
                        case 'effective': renderEffectivePom(rawPomText); break;
                        case 'raw': renderRawPom(rawPomText); break;
                    }
                });
            }
            break;
        case 'pomUpdated':
            // Document was saved by the extension; received fresh parsed data
            rawPomText = msg.rawText || '';
            latestPomData = msg.pomData;
            renderOverview(latestPomData);
            renderRawPom(rawPomText);
            // Clear cache for other tabs so they re-render on next visit
            renderedTabs.delete('hierarchy');
            renderedTabs.delete('effective');
            break;
    }
});

// ---- RENDER: Overview ----
function renderOverview(data) {
    if (!data) return;
    const a = data.artifact || {};
    document.getElementById('ov-groupId').textContent = a.groupId || '-';
    document.getElementById('ov-artifactId').textContent = a.artifactId || '-';
    document.getElementById('ov-version').textContent = a.version || '-';
    document.getElementById('ov-packaging').textContent = a.packaging || 'jar';

    if (data.parent) {
        document.getElementById('ov-parent-groupId').textContent = data.parent.groupId || '-';
        document.getElementById('ov-parent-artifactId').textContent = data.parent.artifactId || '-';
        document.getElementById('ov-parent-version').textContent = data.parent.version || '-';
    }

    // Properties
    const propsContainer = document.getElementById('ov-properties');
    const keys = Object.keys(data.properties || {});
    if (keys.length === 0) {
        propsContainer.innerHTML =
            '<div class="empty-state"><span class="empty-icon">&#128196;</span><span>No properties</span></div>';
    } else {
        propsContainer.innerHTML = keys.map(k =>
            '<div class="prop-row">' +
            '<span class="prop-key">' + escHtml(k) + '</span>' +
            '<span class="prop-eq">=</span>' +
            '<span class="prop-val">' + escHtml(data.properties[k]) + '</span>' +
            '</div>'
        ).join('');
    }
}

// ---- RENDER: Dependency Hierarchy ----
function renderDependencyHierarchy(data) {
    // Build a simple tree from dependencies in pom
    const deps = (data && data.dependencies) || [];
    const treeEl = document.getElementById('depTree');
    const listEl = document.getElementById('depList');

    if (deps.length === 0) {
        treeEl.innerHTML =
            '<div class="empty-state"><span class="empty-icon">&#127795;</span><span>No dependencies found</span></div>';
        listEl.innerHTML =
            '<div class="empty-state"><span class="empty-icon">&#128230;</span><span>No resolved dependencies</span></div>';
        return;
    }

    // Tree view
    treeEl.innerHTML =
        '<div class="tree-node" onclick="toggleTree(this)">' +
        '<span class="tree-caret">&#9660;</span>' +
        '<span class="tree-icon">&#128230;</span>' +
        '<span class="tree-label">Dependencies (' + deps.length + ')</span>' +
        '</div>' +
        '<div class="tree-children expanded">' +
        deps.map((d) =>
            '<div class="tree-node">' +
            '<span class="tree-caret empty">&#9656;</span>' +
            '<span class="tree-icon">&#128196;</span>' +
            '<span class="tree-label">' + escHtml(d.artifactId) +
            ' <span style="color:var(--desc);font-size:11px">' +
            escHtml(d.version || '') + '</span></span>' +
            '</div>'
        ).join('') +
        '</div>';

    // Resolved list
    listEl.innerHTML = deps.map(d =>
        '<div class="dep-list-item">' +
        '<span class="dep-list-gav">' +
        escHtml(d.groupId) + ' : ' + escHtml(d.artifactId) + ' : ' + escHtml(d.version || '(managed)') +
        '</span>' +
        (d.scope ? '<span class="dep-list-scope">' + escHtml(d.scope) + '</span>' : '') +
        '</div>'
    ).join('');
}

// ---- RENDER: Effective POM ----
function renderEffectivePom(text) {
    // For now, just show the raw pom as a placeholder for effective POM
    document.getElementById('effectivePomText').value = text || '';
}

// ---- RENDER: Raw pom.xml ----
let rawPomOriginal = '';

function renderRawPom(text) {
    rawPomOriginal = text || '';
    document.getElementById('rawPomText').value = rawPomOriginal;
}

// ---- Save handler: Ctrl+S / Cmd+S in pom.xml tab ----
document.addEventListener('keydown', function (e) {
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        const textarea = document.getElementById('rawPomText');
        const newText = textarea.value;
        if (newText !== rawPomOriginal) {
            vscode.postMessage({ type: 'updatePom', text: newText });
        }
    }
});

// ---- Tree toggle ----
function toggleTree(node) {
    const caret = node.querySelector('.tree-caret');
    const children = node.nextElementSibling;
    if (children && children.classList.contains('tree-children')) {
        const expanded = children.classList.toggle('expanded');
        caret.innerHTML = expanded ? '&#9660;' : '&#9656;';
    }
}

// ---- Splitter drag ----
(function () {
    const splitter = document.getElementById('depSplitter');
    const leftPanel = document.getElementById('depTreePanel');
    const container = document.getElementById('depPanels');
    let dragging = false;

    splitter.addEventListener('mousedown', function (e) {
        dragging = true;
        splitter.classList.add('active');
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none';
        e.preventDefault();
    });

    document.addEventListener('mousemove', function (e) {
        if (!dragging) { return; }
        const rect = container.getBoundingClientRect();
        const splitterW = splitter.offsetWidth;
        const x = e.clientX - rect.left;
        const minLeft = 120;
        const maxLeft = rect.width - splitterW - 120;
        const clamped = Math.min(Math.max(x, minLeft), maxLeft);
        const pct = (clamped / rect.width) * 100;
        leftPanel.style.width = pct + '%';
    });

    document.addEventListener('mouseup', function () {
        if (!dragging) { return; }
        dragging = false;
        splitter.classList.remove('active');
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
    });
})();

// ---- Filter (stub) ----
document.getElementById('depFilter').addEventListener('input', function() {
    // Filter logic will be implemented later
});
document.getElementById('btnRefresh').addEventListener('click', function() {
    vscode.postMessage({ type: 'ready' });
});

// ---- Helpers ----
function escHtml(s) {
    const d = document.createElement('div');
    d.textContent = String(s);
    return d.innerHTML;
}

// Request initial data
vscode.postMessage({ type: 'ready' });

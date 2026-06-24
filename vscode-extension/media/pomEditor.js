const vscode = acquireVsCodeApi();
let rawPomText = '';
let latestPomData = null;
let hierarchyData = null;
let lastHierarchyPomText = '';
let effectivePomData = null;
let lastEffectivePomText = '';
let treeData = null;
let allDepsData = null;
const renderedTabs = new Set();

// ---- Tab switching ----
document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        const tabName = btn.dataset.page;
        ensureTabRendered(tabName);

        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-page').forEach(p => p.classList.remove('active'));
        btn.classList.add('active');
        document.getElementById('page-' + tabName).classList.add('active');
    });
});

function ensureTabRendered(tabName) {
    if (tabName === 'hierarchy' && lastHierarchyPomText === rawPomText && hierarchyData) {
        renderedTabs.add(tabName);
        return;
    }
    if (tabName === 'effective' && lastEffectivePomText === rawPomText && effectivePomData) {
        renderedTabs.add(tabName);
        return;
    }
    if (renderedTabs.has(tabName)) return;
    renderedTabs.add(tabName);
    switch (tabName) {
        case 'overview':
            renderOverview(latestPomData);
            break;
        case 'hierarchy':
            lastHierarchyPomText = rawPomText;
            requestHierarchy();
            break;
        case 'effective':
            lastEffectivePomText = rawPomText;
            requestEffectivePom();
            break;
        case 'raw':
            renderRawPom(rawPomText);
            break;
    }
}

function requestHierarchy() {
    showLoading('depTree', 'Requesting dependency tree...');
    showLoading('depList', 'Waiting...');
    vscode.postMessage({ type: 'requestHierarchy' });
}

function requestEffectivePom() {
    showLoading('effectivePomContainer', 'Requesting effective POM...');
    vscode.postMessage({ type: 'requestEffectivePom' });
}

function showLoading(elementId, message) {
    const el = document.getElementById(elementId);
    if (!el) return;
    el.innerHTML = '<div class="empty-state"><div class="spinner"></div><span>' + escHtml(message) + '</span></div>';
}

// ---- Messages from extension ----
window.addEventListener('message', e => {
    const msg = e.data;
    switch (msg.type) {
        case 'setPomData':
            rawPomText = msg.rawText || '';
            latestPomData = msg.pomData;
            if (renderedTabs.size === 0) {
                ensureTabRendered('overview');
            } else {
                renderedTabs.forEach(tab => {
                    switch (tab) {
                        case 'overview': renderOverview(latestPomData); break;
                        case 'raw': renderRawPom(rawPomText); break;
                    }
                });
            }
            break;

        case 'pomUpdated':
            rawPomText = msg.rawText || '';
            latestPomData = msg.pomData;
            hierarchyData = null;
            lastHierarchyPomText = '';
            effectivePomData = null;
            lastEffectivePomText = '';
            currentFilterQuery = '';
            document.getElementById('depFilter').value = '';
            renderOverview(latestPomData);
            renderRawPom(rawPomText);
            renderedTabs.delete('hierarchy');
            renderedTabs.delete('effective');

            const activeBtn = document.querySelector('.tab-btn.active');
            if (activeBtn) {
                const activeTab = activeBtn.dataset.page;
                if (activeTab === 'hierarchy') {
                    ensureTabRendered('hierarchy');
                } else if (activeTab === 'effective') {
                    ensureTabRendered('effective');
                }
            }
            break;

        case 'backendStatus':
            if (msg.status === 'starting' || msg.status === 'stopped') {
                if (!hierarchyData && renderedTabs.has('hierarchy')) {
                    showLoading('depTree', 'Backend is starting, please wait...');
                    showLoading('depList', 'Waiting...');
                }
                if (!effectivePomData && renderedTabs.has('effective')) {
                    showLoading('effectivePomContainer', 'Backend is starting, please wait...');
                }
            } else if (msg.status === 'loading') {
                if (!hierarchyData && renderedTabs.has('hierarchy')) {
                    showLoading('depTree', 'Resolving dependencies...');
                    showLoading('depList', 'Waiting...');
                }
                if (!effectivePomData && renderedTabs.has('effective')) {
                    showLoading('effectivePomContainer', 'Resolving effective POM...');
                }
            }
            break;

        case 'hierarchyResult':
            hierarchyData = msg.result;
            if (msg.result.success) {
                renderDependencyTree(msg.result.data);
            } else {
                document.getElementById('depTree').innerHTML =
                    '<div class="empty-state error"><span class="empty-icon">&#9888;</span><span>' +
                    escHtml(msg.result.errorMessage || 'Resolution failed') + '</span></div>';
                document.getElementById('depList').innerHTML = '';
            }
            break;

        case 'effectivePomResult':
            effectivePomData = msg;
            if (msg.success) {
                renderEffectivePom(msg.effectivePom || '');
            } else {
                document.getElementById('effectivePomContainer').innerHTML =
                    '<div class="empty-state error"><span class="empty-icon">&#9888;</span><span>' +
                    escHtml(msg.errorMessage || 'Failed to get effective POM') + '</span></div>';
            }
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

// ---- RENDER: Dependency Tree ----
function renderDependencyTree(data) {
    const directDeps = data.directDependencies || [];
    const allDeps = data.allDependencies || [];
    const conflicts = data.conflicts || [];

    treeData = directDeps;
    allDepsData = allDeps;

    // Build conflict lookup: gaKey -> { version, conflictingVersions }
    const conflictMap = {};
    conflicts.forEach(c => {
        conflictMap[c.groupId + ':' + c.artifactId] = c;
    });

    // Build winner version map
    const gaVersionMap = {};
    allDeps.forEach(d => {
        gaVersionMap[d.groupId + ':' + d.artifactId] = d.version;
    });

    // Render tree
    const treeEl = document.getElementById('depTree');
    if (directDeps.length === 0) {
        treeEl.innerHTML =
            '<div class="empty-state"><span class="empty-icon">&#127795;</span><span>No dependencies</span></div>';
    } else {
        treeEl.innerHTML = renderTreeNode(directDeps, gaVersionMap, conflictMap, 0);
    }

    // Bind toggle handlers
    treeEl.querySelectorAll('.tree-caret').forEach(caret => {
        caret.addEventListener('click', function(e) {
            e.stopPropagation();
            if (this.classList.contains('empty')) return;
            const children = this.parentElement.nextElementSibling;
            if (children && children.classList.contains('tree-children')) {
                const expanded = children.classList.toggle('expanded');
                this.classList.toggle('collapsed', !expanded);
                this.innerHTML = expanded ? '&#9660;' : '&#9656;';
            }
        });
    });

    // Render resolved list
    renderResolvedList(allDeps);

    // Expand / collapse all buttons
    document.getElementById('btnExpandAll').onclick = function () {
        treeEl.querySelectorAll('.tree-children').forEach(c => c.classList.add('expanded'));
        treeEl.querySelectorAll('.tree-caret:not(.empty)').forEach(c => {
            c.classList.remove('collapsed');
            c.innerHTML = '&#9660;';
        });
    };
    document.getElementById('btnCollapseAll').onclick = function () {
        treeEl.querySelectorAll('.tree-children').forEach(c => c.classList.remove('expanded'));
        treeEl.querySelectorAll('.tree-caret:not(.empty)').forEach(c => {
            c.classList.add('collapsed');
            c.innerHTML = '&#9656;';
        });
    };

    // Sort toggle
    let sortAlpha = false;
    document.getElementById('btnSortAlpha').onclick = function () {
        sortAlpha = !sortAlpha;
        this.classList.toggle('active', sortAlpha);
        const sorted = sortAlpha
            ? [...allDeps].sort((a, b) => (a.groupId + ':' + a.artifactId).localeCompare(b.groupId + ':' + b.artifactId))
            : allDeps;
        renderResolvedList(sorted);
        applyListFilter(currentFilterQuery);
    };
}

function renderResolvedList(deps) {
    const listEl = document.getElementById('depList');
    if (deps.length === 0) {
        listEl.innerHTML =
            '<div class="empty-state"><span class="empty-icon">&#128230;</span><span>No resolved dependencies</span></div>';
    } else {
        listEl.innerHTML = deps.map(d =>
            '<div class="dep-list-item' + (!d.resolved ? ' unresolved' : '') + '" data-gav="' +
            escHtml(d.groupId + ':' + d.artifactId + ':' + (d.version || '')) + '">' +
            '<span class="dep-list-gav">' +
            escHtml(d.groupId) + ' : ' + escHtml(d.artifactId) + ' : ' + escHtml(d.version || '(managed)') +
            '</span>' +
            (d.scope ? '<span class="dep-list-scope">' + escHtml(d.scope) + '</span>' : '') +
            (!d.resolved ? '<span class="dep-list-warn">not found</span>' : '') +
            '</div>'
        ).join('');
    }
}

function renderTreeNode(deps, gaVersionMap, conflictMap, depth) {
    let html = '';
    const indentPx = depth * 16;
    deps.forEach(d => {
        const ga = d.groupId + ':' + d.artifactId;
        const winnerVersion = gaVersionMap[ga];
        const isConflict = d.version !== winnerVersion;
        const cInfo = conflictMap[ga];
        const isUnresolved = !d.resolved;

        let rowClass = 'tree-node';
        if (isConflict) rowClass += ' conflict';
        if (isUnresolved) rowClass += ' unresolved';
        if (depth === 0) rowClass += ' root';

        const hasChildren = d.children && d.children.length > 0;

        html += '<div class="' + rowClass + '" style="padding-left:' + (indentPx + 8) + 'px" data-gav="' +
            escHtml(d.groupId + ':' + d.artifactId + ':' + (d.version || '')) + '">';
        html += '<span class="tree-caret' + (!hasChildren ? ' empty' : ' collapsed') + '">' +
            (hasChildren ? '&#9656;' : '&#9656;') + '</span>';
        html += '<span class="tree-icon">' + (isUnresolved ? '&#9888;' : '&#128196;') + '</span>';
        html += '<span class="tree-label">';
        html += escHtml(d.artifactId) + ' ';
        html += '<span class="tree-version' + (isConflict ? ' conflict-ver' : '') + (isUnresolved ? ' unresolved-ver' : '') + '">';
        html += escHtml(d.version || '');
        html += '</span>';

        if (isConflict && winnerVersion) {
            html += ' <span class="conflict-badge" title="omitted for conflict with ' +
                escHtml(winnerVersion) + '">omitted for conflict with ' + escHtml(winnerVersion) + '</span>';
        }
        if (isUnresolved && d.errorMessage) {
            html += ' <span class="unresolved-badge" title="' + escHtml(d.errorMessage) + '">unresolved</span>';
        }
        html += '</span>';
        html += '<span class="tree-scope">' + escHtml(d.scope || '') + '</span>';
        html += '</div>';

        if (hasChildren) {
            html += '<div class="tree-children">';
            html += renderTreeNode(d.children, gaVersionMap, conflictMap, depth + 1);
            html += '</div>';
        }
    });
    return html;
}

// ---- RENDER: Effective POM ----
function renderEffectivePom(xml) {
    const textarea = document.getElementById('effectivePomText');
    if (textarea) {
        textarea.value = xml || '';
    } else {
        const container = document.getElementById('effectivePomContainer');
        if (container) {
            container.innerHTML = '<textarea readonly id="effectivePomText">' +
                escHtml(xml || '') + '</textarea>';
        }
    }
}

// ---- RENDER: Raw pom.xml ----
let rawPomOriginal = '';

function renderRawPom(text) {
    rawPomOriginal = text || '';
    document.getElementById('rawPomText').value = rawPomOriginal;
}

// Sync textarea changes to VS Code document to mark dirty
document.getElementById('rawPomText').addEventListener('input', function () {
    if (this.value !== rawPomOriginal) {
        vscode.postMessage({ type: 'syncText', text: this.value });
    }
});

// ---- Save & undo handlers ----
document.addEventListener('keydown', function (e) {
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        const textarea = document.getElementById('rawPomText');
        const newText = textarea.value;
        if (newText !== rawPomOriginal) {
            vscode.postMessage({ type: 'updatePom', text: newText });
        }
    }
    if ((e.ctrlKey || e.metaKey) && e.key === 'z') {
        const textarea = document.getElementById('rawPomText');
        if (textarea && document.activeElement === textarea) {
            e.stopPropagation();
        }
    }
});

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
        if (!dragging) return;
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
        if (!dragging) return;
        dragging = false;
        splitter.classList.remove('active');
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
    });
})();

// ---- Filter ----
let currentFilterQuery = '';

document.getElementById('depFilter').addEventListener('keydown', function (e) {
    if (e.key !== 'Enter') return;
    currentFilterQuery = this.value.trim().toLowerCase();
    applyFilter();
});

function applyFilter() {
    const query = currentFilterQuery;
    const treeEl = document.getElementById('depTree');
    const allNodes = treeEl.querySelectorAll('.tree-node');
    const allChildren = treeEl.querySelectorAll('.tree-children');

    // Clear tree filter state
    allNodes.forEach(n => n.classList.remove('hidden'));
    allChildren.forEach(c => c.classList.remove('always-expanded'));

    if (!query) {
        applyListFilter('');
        return;
    }

    // Mark matching nodes
    const matched = new Set();
    allNodes.forEach(node => {
        const label = node.querySelector('.tree-label');
        if (label && label.textContent.toLowerCase().includes(query)) {
            matched.add(node);
        }
    });

    // Mark ancestors of matching nodes
    allNodes.forEach(node => {
        if (!matched.has(node)) return;
        let parent = node.parentElement;
        while (parent) {
            if (parent.classList.contains('tree-children')) {
                parent.classList.add('always-expanded');
                const prev = parent.previousElementSibling;
                if (prev && prev.classList.contains('tree-node')) {
                    matched.add(prev);
                }
            }
            parent = parent.parentElement;
        }
    });

    // Show/hide nodes
    allNodes.forEach(n => n.classList.toggle('hidden', !matched.has(n)));

    // Filter list
    applyListFilter(query);
}

function applyListFilter(query) {
    const listItems = document.querySelectorAll('#depList .dep-list-item');
    if (!query) {
        listItems.forEach(item => item.classList.remove('filtered-out'));
    } else {
        listItems.forEach(item => {
            const gav = item.querySelector('.dep-list-gav');
            const match = gav && gav.textContent.toLowerCase().includes(query);
            item.classList.toggle('filtered-out', !match);
        });
    }
}

document.getElementById('btnRefresh').addEventListener('click', function () {
    hierarchyData = null;
    lastHierarchyPomText = '';
    effectivePomData = null;
    lastEffectivePomText = '';
    renderedTabs.delete('hierarchy');
    renderedTabs.delete('effective');
    currentFilterQuery = '';
    document.getElementById('depFilter').value = '';
    ensureTabRendered('hierarchy');
});

// ---- Helpers ----
function escHtml(s) {
    const d = document.createElement('div');
    d.textContent = String(s);
    return d.innerHTML;
}

// ---- Context menu ----
const ctxMenuEl = document.getElementById('contextMenu');
let ctxMenuTarget = null;

document.addEventListener('contextmenu', function (e) {
    const node = e.target.closest('.tree-node, .dep-list-item');
    if (!node || !node.dataset.gav) {
        if (ctxMenuEl) ctxMenuEl.classList.remove('visible');
        return;
    }
    e.preventDefault();
    e.stopPropagation();
    ctxMenuTarget = node;
    if (ctxMenuEl) {
        ctxMenuEl.style.left = e.clientX + 'px';
        ctxMenuEl.style.top = e.clientY + 'px';
        ctxMenuEl.classList.add('visible');
    }
});

document.addEventListener('click', function () {
    if (ctxMenuEl) ctxMenuEl.classList.remove('visible');
    ctxMenuTarget = null;
});

const ctxCopyBtn = document.getElementById('ctxCopy');
if (ctxCopyBtn) {
    ctxCopyBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        if (ctxMenuTarget) {
            const parts = ctxMenuTarget.dataset.gav.split(':');
            const gid = parts[0] || '';
            const aid = parts[1] || '';
            const ver = parts.slice(2).join(':') || '';
            const xml = '<dependency>\n  <groupId>' + gid + '</groupId>\n  <artifactId>' + aid + '</artifactId>\n  <version>' + ver + '</version>\n</dependency>';
            navigator.clipboard.writeText(xml);
        }
        if (ctxMenuEl) ctxMenuEl.classList.remove('visible');
        ctxMenuTarget = null;
    });
}

// ---- Lock version ----
const ctxLockBtn = document.getElementById('ctxLock');
if (ctxLockBtn) {
    ctxLockBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        if (ctxMenuEl) ctxMenuEl.classList.remove('visible');
        if (ctxMenuTarget) {
            const parts = ctxMenuTarget.dataset.gav.split(':');
            document.getElementById('lockGroupId').value = parts[0] || '';
            document.getElementById('lockArtifactId').value = parts[1] || '';
            document.getElementById('lockVersion').value = parts.slice(2).join(':') || '';
            document.getElementById('lockModal').classList.add('visible');
            document.getElementById('lockVersion').focus();
            document.getElementById('lockVersion').select();
        }
        ctxMenuTarget = null;
    });
}

document.getElementById('lockCancel').addEventListener('click', function () {
    document.getElementById('lockModal').classList.remove('visible');
});

document.getElementById('lockConfirm').addEventListener('click', function () {
    const version = document.getElementById('lockVersion').value.trim();
    if (!version) return;
    const groupId = document.getElementById('lockGroupId').value;
    const artifactId = document.getElementById('lockArtifactId').value;
    document.getElementById('lockModal').classList.remove('visible');
    vscode.postMessage({
        type: 'lockVersion',
        groupId: groupId,
        artifactId: artifactId,
        version: version,
    });
});

// Close modal on overlay click
document.getElementById('lockModal').addEventListener('click', function (e) {
    if (e.target === this) {
        this.classList.remove('visible');
    }
});

vscode.postMessage({ type: 'ready' });

<?php
require_once 'config/db.php';
require_once 'config/links.php';
$db         = getDb();
$pageTitle  = 'Developer – Test Dashboard';
$activePage = 'developer';

include 'partials/header.php';
include 'partials/filters.php';
?>

<div class="main">


<!-- Filter runov -->
<div style="margin-bottom:8px;display:flex;align-items:center;gap:10px;flex-wrap:wrap;
            padding:10px 12px;background:var(--bg2);border:1px solid var(--border);border-radius:6px">
    <span class="filter-label">Filter runov</span>
    <input type="text" id="run-filter" class="filter-input"
           placeholder="🔍 Build / branch / commit / environment..."
           style="width:280px" oninput="applyRunFilter()">
    <span class="muted" id="run-filter-info" style="font-size:12px"></span>
    <div class="filter-sep"></div>
    <select id="trigger-filter" class="filter-input" style="width:160px" onchange="applyRunFilter()">
        <option value="">Všetky typy</option>
        <option value="manual">🖱 Manual</option>
        <option value="scheduled">🕐 Scheduled</option>
        <option value="dependency">🔗 Dependency</option>
        <option value="remote">⚡ Remote / API</option>
    </select>
    <button class="quick-btn" onclick="clearRunFilter()">✕</button>
</div>

<!-- Filter scenárov -->
<div style="margin-bottom:12px;display:flex;align-items:center;gap:10px;flex-wrap:wrap;
            padding:10px 12px;background:var(--bg2);border:1px solid var(--border);border-radius:6px">
    <span class="filter-label">Filter scenárov</span>
    <input type="text" id="scen-filter" class="filter-input"
           placeholder="🔍 Názov / tag / JIRA-123 / @smoke / regex..."
           style="width:300px" oninput="applyScenFilter()">
    <span class="muted" id="scen-filter-info" style="font-size:12px"></span>
    <button class="quick-btn" onclick="clearScenFilter()">✕</button>
    <div class="filter-sep"></div>
    <button class="quick-btn" id="failed-toggle" onclick="toggleFailedOnly()"
            title="Zobraziť len failed scenáre">❌ Len failed</button>
</div>

<!-- Runs list — each run has its own inline expand -->
<div id="runs-container">
    <div class="table-wrap">
        <div class="table-header">
            <span class="table-title">Runy</span>
            <span class="table-count" id="runs-count"></span>
        </div>
        <div id="runs-body">
            <div class="empty">
                <div class="spinner" style="margin:0 auto 8px;display:block;width:24px;height:24px"></div>
                Načítavam...
            </div>
        </div>
    </div>
</div>

</div>

<script>
const fp = filterParams();
var scenFilterRegex = null;
var runFilterRegex  = null;
var triggerFilter   = '';
var failedOnly      = false;
const BAMBOO_URL       = '<?= BAMBOO_URL ?>';
const JIRA_URL         = '<?= JIRA_URL ?>';
const JIRA_CYCLE_PATH  = '<?= JIRA_CYCLE_PATH ?>';

// ── Load runs ────────────────────────────────────────────────
async function loadRuns() {
    const data = await fetchJson(
        `api/dev_data.php?action=runs&from=${fp.from}&to=${fp.to}&project=${fp.project}&parallel=${fp.parallel||'all'}&jira=${fp.jira||'all'}`
    );
    document.getElementById('runs-count').textContent = data.length + ' runov';
    if (!data.length) {
        document.getElementById('runs-body').innerHTML =
            '<div class="empty"><div class="empty-icon">📭</div>Žiadne dáta</div>';
        return;
    }

    let html = '<table><thead><tr>'
        + '<th></th><th>Dátum</th><th>Branch</th><th>Commit</th><th>Build</th><th>Beh</th>'
        + '<th>Scenáre ✓/✗</th><th>Úspešnosť</th><th>Čas buildu</th>'
        + '</tr></thead><tbody id="runs-tbody">';

    data.forEach(r => {
        const rate   = r.success_rate || 0;
        const commit = r.commit_hash ? r.commit_hash.substring(0,8) : '—';
        const isP    = r.parallel_mode === 'parallel';
        const runSearch = [r.build_number, r.branch, r.commit_hash,
                           r.environment, r.triggered_by, r.app_url
                          ].filter(Boolean).join(' ').toLowerCase();
        html += `
        <tr data-id="${r.id}" data-run-id="${r.id}"
            data-run-search="${runSearch}"
            data-run-failed="${r.failed_scenarios > 0 ? 'yes' : 'no'}"
            data-trigger-type="${(r.trigger_type||'unknown').toLowerCase()}"
            data-trigger-type="${(r.trigger_type||'unknown').toLowerCase()}">
            <td style="width:32px">
                <button class="expand-btn" id="rbtn-${r.id}" onclick="toggleRun(this,${r.id},${JSON.stringify(r).replace(/"/g,'&quot;')})">+</button>
            </td>
            <td class="nowrap">${formatDate(r.run_at)}</td>
            <td class="mono">${escHtml(r.branch||'—')}</td>
            <td class="mono" title="${escHtml(r.commit_hash||'')}">${escHtml(commit)}</td>
            <td class="mono">${BAMBOO_URL
                ? `<a href="${BAMBOO_URL}/browse/${escHtml(r.build_number)}" target="_blank">${escHtml(r.build_number)}</a>`
                : escHtml(r.build_number)}</td>
            <td>${isP
                ? `<span style="color:var(--accent)">⚡ ×${r.thread_count}</span>`
                : '<span class="muted">serial</span>'}</td>
            <td>
                <span style="color:var(--success)">✓${r.passed_scenarios}</span>
                <span style="color:var(--danger)"> ✗${r.failed_scenarios}</span>
                / ${r.total_scenarios}
            </td>
            <td><span class="pct ${rate>=80?'good':'bad'}">${rate}%</span></td>
            <td class="nowrap">${formatDuration(r.build_duration_ms)}</td>
        </tr>
        <!-- Inline expand row for this run -->
        <tr id="rrow-${r.id}" style="display:none">
            <td colspan="99" style="padding:0;border-top:2px solid var(--accent)">
                <div style="padding:12px 16px 16px 40px;background:var(--bg)">
                    <!-- Run info mini-grid -->
                    <div id="rinfo-${r.id}" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:8px 16px;padding:10px 0 14px 0;border-bottom:1px solid var(--border);margin-bottom:12px;font-size:12px">
                    </div>
                    <!-- Features -->
                    <div id="rfeat-${r.id}">
                        <div style="padding:12px;text-align:center">
                            <div class="spinner" style="margin:0 auto;width:20px;height:20px"></div>
                        </div>
                    </div>
                </div>
            </td>
        </tr>`;
    });
    html += '</tbody></table>';
    document.getElementById('runs-body').innerHTML = html;
}

// ── Toggle run expand ────────────────────────────────────────
function toggleRun(btn, runId, runData) {
    const row  = document.getElementById('rrow-' + runId);
    const open = row.style.display === 'none';
    row.style.display = open ? 'table-row' : 'none';
    btn.classList.toggle('open', open);
    if (open && !row.dataset.loaded) {
        row.dataset.loaded = '1';
        renderRunInfo(runId, runData);
        loadFeatures(runId);
    }
}

function renderRunInfo(runId, r) {
    const isP    = r.parallel_mode === 'parallel';
    const isJira = r.jira_reporting == 1 || r.jira_reporting === true;
    const items  = [
        ['Spustil',      r.triggered_by || '—'],
        ['Typ',          r.trigger_type || '—'],
        ['Prostredie',   r.environment  || '—'],
        ['Beh',          isP ? `⚡ parallel ×${r.thread_count}` : 'serial'],
        ['Σ Trvanie',    formatDuration(r.tests_total_ms)],
        ['⏱ Čas buildu', formatDuration(r.build_duration_ms)],
        ['Jira',         isJira
            ? '<span style="color:var(--success)">✓ reportuje do Jiry</span>'
            : '<span class="muted">test run</span>'],
        ...(isJira ? [['Nová exekúcia', r.jira_new_execution == 1
            ? '<span style="color:var(--accent)">✓ vždy nová exekúcia</span>'
            : '<span class="muted">reuse existujúcej</span>']] : []),
    ];
    if (r.app_url)         items.push(['URL',                `<a href="${escHtml(r.app_url)}" target="_blank" style="word-break:break-all">${escHtml(r.app_url)}</a>`, 'wide']);
    if (r.cucumber_tags)   items.push(['Cucumber tagy',       `<span class="mono" style="font-size:11px">${escHtml(r.cucumber_tags)}</span>`, 'wide']);
    if (r.tested_feature)  items.push(['Testované features',  `<span class="mono" style="font-size:11px;word-break:break-all">${escHtml(r.tested_feature)}</span>`, 'full']);
    if (r.test_properties) items.push(['Property file',       `<span class="mono" style="font-size:11px;word-break:break-all">${escHtml(r.test_properties)}</span>`, 'full']);
    if (r.jira_cycle_ids) {
        const cycles = r.jira_cycle_ids.split(',').map(c => c.trim()).filter(Boolean);
        if (cycles.length > 0) {
            const links = cycles.map(c => {
                const url = JIRA_URL + JIRA_CYCLE_PATH.replace('{cycle}', c);
                return `<a href="${url}" target="_blank" style="display:inline-block;background:var(--accent-dim);color:var(--accent);border-radius:10px;padding:1px 8px;font-size:12px;margin:1px;text-decoration:none">🔗 ${escHtml(c)}</a>`;
            }).join(' ');
            items.push(['Jira Cycles', links]);
        }
    }
    document.getElementById('rinfo-' + runId).innerHTML = items.map(([k,v,span]) => {
        const col = span === 'full' ? '1 / -1' : span === 'wide' ? 'span 2' : '';
        const style = col ? `grid-column:${col}` : '';
        return `<div style="${style};min-width:0"><div style="color:var(--text-muted);font-size:10px;text-transform:uppercase;letter-spacing:.05em;margin-bottom:2px;white-space:nowrap">${k}</div><div style="font-size:12px;word-break:break-word;overflow-wrap:anywhere">${v}</div></div>`;
    }).join('');
}

// ── Load features ────────────────────────────────────────────
async function loadFeatures(runId) {
    const container = document.getElementById('rfeat-' + runId);
    const data = await fetchJson(`api/dev_data.php?action=features&run_id=${runId}`);
    if (!data.length) {
        container.innerHTML = '<div class="empty">Žiadne features</div>';
        return;
    }
    let html = `<div style="font-size:12px;font-weight:600;color:var(--text-muted);text-transform:uppercase;letter-spacing:.05em;margin-bottom:6px">Features (${data.length})</div>
    <table style="width:100%;border-collapse:collapse;background:var(--bg2);border-radius:6px;overflow:hidden">
    <thead><tr style="background:var(--bg3)">
        <th style="padding:8px 10px;text-align:left;font-size:11px;color:var(--text-muted)"></th>
        <th style="padding:8px 10px;text-align:left;font-size:11px;color:var(--text-muted)">Feature</th>
        <th style="padding:8px 10px;text-align:left;font-size:11px;color:var(--text-muted)">Tagy</th>
        <th style="padding:8px 10px;text-align:left;font-size:11px;color:var(--text-muted)">Status</th>
        <th style="padding:8px 10px;text-align:left;font-size:11px;color:var(--text-muted)">Scenáre</th>
        <th style="padding:8px 10px;text-align:left;font-size:11px;color:var(--text-muted)">Trvanie</th>
    </tr></thead><tbody>`;

    data.forEach(f => {
        html += `
        <tr data-feat-id="${f.id}" data-run-ref="${runId}" style="border-top:1px solid var(--border)">
            <td style="padding:8px 10px;width:32px">
                <button class="expand-btn" id="fbtn-${f.id}" onclick="toggleFeat(this,${f.id})">+</button>
            </td>
            <td style="padding:8px 10px">
                <div>${escHtml(f.name)}</div>
                <div class="muted mono" style="font-size:11px">${escHtml(f.file_path||'')}</div>
            </td>
            <td style="padding:8px 10px">${renderTags(f.tags)}</td>
            <td style="padding:8px 10px">${badge(f.status)}</td>
            <td style="padding:8px 10px">
                <span style="color:var(--success)">✓${f.passed_scenarios}</span>
                <span style="color:var(--danger)"> ✗${f.failed_scenarios}</span>
                / ${f.total_scenarios}
            </td>
            <td style="padding:8px 10px;white-space:nowrap">${formatDuration(f.duration_ms)}</td>
        </tr>
        <tr id="frow-${f.id}" style="display:none">
            <td colspan="99" style="padding:0;border-top:1px solid var(--accent-dim)">
                <div style="padding:10px 10px 12px 48px;background:var(--bg)">
                    <div id="fscen-${f.id}">
                        <div style="padding:10px;text-align:center">
                            <div class="spinner" style="margin:0 auto;width:18px;height:18px"></div>
                        </div>
                    </div>
                </div>
            </td>
        </tr>`;
    });
    html += '</tbody></table>';
    container.innerHTML = html;
}

async function toggleFeat(btn, featId) {
    const row  = document.getElementById('frow-' + featId);
    const open = row.style.display === 'none';
    row.style.display = open ? 'table-row' : 'none';
    btn.classList.toggle('open', open);
    if (open && !row.dataset.loaded) {
        row.dataset.loaded = '1';
        loadScenarios(featId);
    }
}

// ── Scenarios HTML builder (shared by loadScenarios and auto-filter) ──
function buildScenariosHtml(data, featId) {
    let html = `<div style="font-size:12px;font-weight:600;color:var(--text-muted);text-transform:uppercase;letter-spacing:.05em;margin-bottom:6px">Scenáre (${data.length})</div>
    <table style="width:100%;border-collapse:collapse;background:var(--bg3);border-radius:6px;overflow:hidden">
    <thead><tr style="background:var(--bg2)">
        <th style="padding:7px 10px;text-align:left;font-size:11px;color:var(--text-muted)"></th>
        <th style="padding:7px 10px;text-align:left;font-size:11px;color:var(--text-muted)">Scenár</th>
        <th style="padding:7px 10px;text-align:left;font-size:11px;color:var(--text-muted)">Tagy</th>
        <th style="padding:7px 10px;text-align:left;font-size:11px;color:var(--text-muted)">Status</th>
        <th style="padding:7px 10px;text-align:left;font-size:11px;color:var(--text-muted)">Stepy</th>
        <th style="padding:7px 10px;text-align:left;font-size:11px;color:var(--text-muted)">Riadok</th>
        <th style="padding:7px 10px;text-align:left;font-size:11px;color:var(--text-muted)">Trvanie</th>
    </tr></thead><tbody>`;
    data.forEach(s => {
        const scenText = [s.name, s.tags||''].join(' ').toLowerCase();
        html += `
        <tr data-scen-name="${escHtml(scenText)}" data-scen-status="${s.status}" data-feat-ref="${featId}" style="border-top:1px solid var(--border)">
            <td style="padding:7px 10px;width:32px">
                <button class="expand-btn" id="sbtn-${s.id}" onclick="toggleScen(this,${s.id})">+</button>
            </td>
            <td style="padding:7px 10px">
                <div>${escHtml(s.name)}</div>
                ${s.hook_error_message ? `<div class="hook-error" style="margin-top:4px"><strong>⚠ ${s.hook_error_type} hook</strong>${escHtml(s.hook_error_message)}</div>` : ''}
            </td>
            <td style="padding:7px 10px">${renderTags(s.tags)}</td>
            <td style="padding:7px 10px">${badge(s.status)}</td>
            <td style="padding:7px 10px">
                <span style="color:var(--success)">✓${s.passed_steps}</span>
                <span style="color:var(--danger)"> ✗${s.failed_steps}</span>
                ${s.skipped_steps>0 ? `<span style="color:var(--skip)"> ⊘${s.skipped_steps}</span>` : ''}
                / ${s.total_steps}
            </td>
            <td style="padding:7px 10px" class="mono muted">${s.line||'—'}</td>
            <td style="padding:7px 10px;white-space:nowrap">${formatDuration(s.duration_ms)}</td>
        </tr>
        <tr id="srow-${s.id}" style="display:none">
            <td colspan="99" style="padding:0;border-top:1px solid var(--border)">
                <div style="padding:8px 10px 12px 56px;background:var(--bg2)">
                    <div id="ssteps-${s.id}">
                        <div style="padding:8px;text-align:center">
                            <div class="spinner" style="margin:0 auto;width:16px;height:16px"></div>
                        </div>
                    </div>
                </div>
            </td>
        </tr>`;
    });
    html += '</tbody></table>';
    return html;
}

// ── Load scenarios ───────────────────────────────────────────
async function loadScenarios(featId) {
    const container = document.getElementById('fscen-' + featId);
    const data = await fetchJson(`api/dev_data.php?action=scenarios&feature_id=${featId}`);
    if (!data.length) {
        container.innerHTML = '<div class="empty" style="padding:10px">Žiadne scenáre</div>';
        return;
    }
    container.innerHTML = buildScenariosHtml(data, featId);
    // Apply existing filters
    reapplyFiltersAfterLoad();
}

async function toggleScen(btn, scenId) {
    const row  = document.getElementById('srow-' + scenId);
    const open = row.style.display === 'none';
    row.style.display = open ? 'table-row' : 'none';
    btn.classList.toggle('open', open);
    if (open && !row.dataset.loaded) {
        row.dataset.loaded = '1';
        loadSteps(scenId);
    }
}

// ── Load steps ───────────────────────────────────────────────
async function loadSteps(scenId) {
    const container = document.getElementById('ssteps-' + scenId);
    const data = await fetchJson(`api/dev_data.php?action=steps&scenario_id=${scenId}`);
    if (!data.length) {
        container.innerHTML = '<div class="empty" style="padding:8px">Žiadne stepy</div>';
        return;
    }
    let html = `<table style="width:100%;border-collapse:collapse"><thead>
        <tr style="background:var(--bg3)">
            <th style="padding:6px 8px;text-align:left;font-size:11px;color:var(--text-muted)">Keyword</th>
            <th style="padding:6px 8px;text-align:left;font-size:11px;color:var(--text-muted)">Step</th>
            <th style="padding:6px 8px;text-align:left;font-size:11px;color:var(--text-muted)">Status</th>
            <th style="padding:6px 8px;text-align:left;font-size:11px;color:var(--text-muted)">Riadok</th>
            <th style="padding:6px 8px;text-align:left;font-size:11px;color:var(--text-muted)">Trvanie</th>
            <th style="padding:6px 8px;text-align:left;font-size:11px;color:var(--text-muted)">📸</th>
        </tr>
    </thead><tbody>`;

    data.forEach(st => {
        html += `<tr style="border-top:1px solid var(--border)">
            <td style="padding:7px 8px;vertical-align:top"><span class="step-keyword">${escHtml(st.keyword||'')}</span></td>
            <td style="padding:7px 8px;vertical-align:top;max-width:480px">
                <div>${escHtml(st.name||'')}</div>
                ${st.translated_text && st.translated_text !== st.name
                    ? `<div class="step-translated">→ ${escHtml(st.translated_text)}</div>` : ''}
                ${st.error_message ? `<div class="error-msg">${escHtml(st.error_message)}</div>` : ''}
                ${st.stack_trace
                    ? `<span class="stack-toggle" onclick="toggleStack(this)">▼ zobraziť stack trace</span><pre class="stack-trace">${escHtml(st.stack_trace)}</pre><span class="stack-expand" onclick="expandStack(this)">↕ zobraziť všetko</span>`
                    : ''}
            </td>
            <td style="padding:7px 8px;vertical-align:top">${badge(st.status)}</td>
            <td style="padding:7px 8px;vertical-align:top" class="mono muted">${st.line||'—'}</td>
            <td style="padding:7px 8px;vertical-align:top;white-space:nowrap">${formatDuration(st.duration_ms)}</td>
            <td style="padding:7px 8px;vertical-align:top">
                ${st.screenshot_url
                    ? `<img class="thumb" src="${escHtml(imgUrl(st.screenshot_url))}" onclick="showImage('${escHtml(imgUrl(st.screenshot_url))}')" alt="">` : ''}
                ${st.failed_screenshot_url
                    ? `<img class="thumb" src="${escHtml(imgUrl(st.failed_screenshot_url))}" onclick="showImage('${escHtml(imgUrl(st.failed_screenshot_url))}')" alt="" style="border-color:var(--danger)">` : ''}
            </td>
        </tr>`;
    });
    html += '</tbody></table>';
    container.innerHTML = html;
}

// ── Scenario filter ──────────────────────────────────────────
// ── Run filter ───────────────────────────────────────────────
function applyRunFilter() {
    const raw  = document.getElementById('run-filter').value.trim();
    const info = document.getElementById('run-filter-info');
    try {
        runFilterRegex = raw ? new RegExp(raw, 'i') : null;
        document.getElementById('run-filter').style.borderColor = '';
        info.textContent = raw ? '✓' : '';
        info.style.color = 'var(--success)';
    } catch(e) {
        runFilterRegex = new RegExp(raw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i');
        document.getElementById('run-filter').style.borderColor = 'var(--warn)';
        info.textContent = '⚠ neplatný regex';
        info.style.color = 'var(--warn)';
    }
    triggerFilter = document.getElementById('trigger-filter').value;
    applyRunLevelFilter();
}

function clearRunFilter() {
    document.getElementById('run-filter').value = '';
    document.getElementById('run-filter-info').textContent = '';
    document.getElementById('run-filter').style.borderColor = '';
    document.getElementById('trigger-filter').value = '';
    runFilterRegex = null;
    triggerFilter  = '';
    applyRunLevelFilter();
}

function applyRunLevelFilter() {
    document.querySelectorAll('tr[data-run-id]').forEach(runRow => {
        const textOK    = !runFilterRegex || runFilterRegex.test(runRow.dataset.runSearch || '');
        const triggerOK = !triggerFilter  || runRow.dataset.triggerType === triggerFilter;
        const show = textOK && triggerOK;
        runRow.style.display = show ? '' : 'none';
        const next = runRow.nextElementSibling;
        if (next && next.id && next.id.startsWith('rrow-') && !show) {
            next.style.display = 'none';
        }
    });
}

// ── Scenario/feature filter ───────────────────────────────────
function toggleFailedOnly() {
    failedOnly = !failedOnly;
    document.getElementById('failed-toggle').classList.toggle('active', failedOnly);
    applyScenLevelFilter();
}

function applyScenFilter() {
    const raw  = document.getElementById('scen-filter').value.trim();
    const info = document.getElementById('scen-filter-info');
    try {
        scenFilterRegex = raw ? new RegExp(raw, 'i') : null;
        document.getElementById('scen-filter').style.borderColor = '';
        info.textContent = raw ? '✓ platný regex' : '';
        info.style.color = 'var(--success)';
    } catch(e) {
        scenFilterRegex = new RegExp(raw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i');
        document.getElementById('scen-filter').style.borderColor = 'var(--warn)';
        info.textContent = '⚠ neplatný regex';
        info.style.color = 'var(--warn)';
    }
    applyScenLevelFilter();
}

function clearScenFilter() {
    document.getElementById('scen-filter').value = '';
    document.getElementById('scen-filter-info').textContent = '';
    document.getElementById('scen-filter').style.borderColor = '';
    scenFilterRegex = null;
    applyScenLevelFilter();
}

function applyScenLevelFilter() {
    const hasFilter = scenFilterRegex || failedOnly;

    // 1. Scenáre
    document.querySelectorAll('tr[data-scen-name]').forEach(row => {
        const show = !hasFilter
            || ((!scenFilterRegex || scenFilterRegex.test(row.dataset.scenName))
             && (!failedOnly      || row.dataset.scenStatus === 'failed'));
        row.style.display = show ? '' : 'none';
        const next = row.nextElementSibling;
        if (next && next.id && next.id.startsWith('srow-') && !show) {
            next.style.display = 'none';
        }
    });

    // 2. Features — ak je filter aktívny, načítaj scenáre pre všetky
    //    features ktoré ich ešte nemajú, potom skry prázdne
    if (hasFilter) {
        const pendingLoads = [];
        document.querySelectorAll('tr[data-feat-id]').forEach(featRow => {
            const featId   = featRow.dataset.featId;
            const scenRows = document.querySelectorAll(`tr[data-feat-ref="${featId}"]`);
            if (scenRows.length === 0) {
                // Scenáre ešte nie sú načítané — načítaj ich teraz
                pendingLoads.push(featRow);
            } else {
                // Scenáre sú načítané — skry feature ak nemá viditeľný scenár
                const show = Array.from(scenRows).some(r => r.style.display !== 'none');
                featRow.style.display = show ? '' : 'none';
                const next = featRow.nextElementSibling;
                if (next && next.id && next.id.startsWith('frow-') && !show) {
                    next.style.display = 'none';
                }
            }
        });

        // Načítaj scenáre pre pending features a po načítaní skry prázdne
        if (pendingLoads.length > 0) {
            Promise.all(pendingLoads.map(async featRow => {
                const featId = featRow.dataset.featId;
                // Načítaj scenáre do kontajnera (ale nerozbaľuj vizuálne)
                const container = document.getElementById('fscen-' + featId);
                if (!container) return;
                const data = await fetchJson(`api/dev_data.php?action=scenarios&feature_id=${featId}`);
                if (!data.length) {
                    // Prázdna feature — skry ju
                    featRow.style.display = 'none';
                    return;
                }
                // Renderuj scenáre (skryté) a potom filter rozhodne
                let html = buildScenariosHtml(data, featId);
                container.innerHTML = html;
                // Aplikuj filter na nové scenáre
                const scenRows = document.querySelectorAll(`tr[data-feat-ref="${featId}"]`);
                scenRows.forEach(row => {
                    const show = (!scenFilterRegex || scenFilterRegex.test(row.dataset.scenName))
                              && (!failedOnly      || row.dataset.scenStatus === 'failed');
                    row.style.display = show ? '' : 'none';
                });
                // Skry feature ak žiadny scenár nevyhovuje
                const anyVisible = Array.from(scenRows).some(r => r.style.display !== 'none');
                featRow.style.display = anyVisible ? '' : 'none';
            }));
        }
    } else {
        // Žiadny filter — obnov všetky features
        document.querySelectorAll('tr[data-feat-id]').forEach(r => r.style.display = '');
    }
}

// Volá sa po načítaní scenárov — preaplikuje scenario filter
function reapplyFiltersAfterLoad() {
    if (scenFilterRegex || failedOnly) applyScenLevelFilter();
}

// Stará funkcia — volá oba filtre
function applyFiltersToAll() {
    applyRunLevelFilter();
    applyScenLevelFilter();
}

// ── Helpers ──────────────────────────────────────────────────
function filterParams() {
    const p = new URLSearchParams(location.search);
    return {
        from:     p.get('from')     || daysAgo(14),
        to:       p.get('to')       || today(),
        project:  p.get('project')  || 'all',
        parallel: p.get('parallel') || 'all',
        jira:     p.get('jira')     || 'all',
    };
}

function renderTags(tags) {
    if (!tags) return '<span class="muted">—</span>';
    return tags.split(',').map(t =>
        `<span style="display:inline-block;background:var(--accent-dim);color:var(--accent);border-radius:10px;padding:1px 7px;font-size:11px;margin:1px">${escHtml(t.trim())}</span>`
    ).join(' ');
}

function badge(status) {
    return `<span class="badge badge-${status}">${status}</span>`;
}

function escHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

// Highlight selected row style
const style = document.createElement('style');
style.textContent = 'tr[data-id].active > td { background: var(--accent-dim); }';
document.head.appendChild(style);

loadRuns();
</script>
</body>
</html>

const token = document.querySelector('meta[name="swordflight-token"]').content;
const headers = { 'Content-Type': 'application/json', 'X-Swordflight-Token': token };
let state = null;
let dirty = false;
let increment = 'patch';
let pollTimer = null;
let selectedRecipeIndex = 0;

const $ = (selector) => document.querySelector(selector);
const el = (tag, className, text) => {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
};

async function api(path, options = {}) {
  const response = await fetch(path, { ...options, headers: { ...headers, ...(options.headers || {}) } });
  const body = await response.json();
  if (!response.ok) throw new Error(body.error || `请求失败 (${response.status})`);
  return body;
}

function toast(message, error = false) {
  const node = $('#toast');
  node.textContent = message;
  node.className = `toast show${error ? ' error' : ''}`;
  clearTimeout(node.timer);
  node.timer = setTimeout(() => node.className = 'toast', 2600);
}

function markDirty() {
  dirty = true;
  const badge = $('#dirtyBadge');
  badge.textContent = '有未保存修改';
  badge.className = 'dirty-badge dirty';
}

function markClean() {
  dirty = false;
  const badge = $('#dirtyBadge');
  badge.textContent = '已保存';
  badge.className = 'dirty-badge clean';
}

function normalizeInput(input, field) {
  let value = Number(input.value);
  if (!Number.isFinite(value)) value = field.value;
  value = Math.max(field.minimum, Math.min(field.maximum, value));
  if (field.integer) value = Math.round(value);
  input.value = value;
  return value;
}

function createNumericField(field, setter, compact = false) {
  const root = el('div', compact ? 'field' : 'parameter-card');
  if (compact) {
    root.append(el('label', 'field-label', field.label));
    const wrap = el('div', 'number-wrap');
    const input = document.createElement('input');
    input.type = 'number'; input.min = field.minimum; input.max = field.maximum; input.step = field.step;
    input.value = field.value;
    input.addEventListener('input', markDirty);
    input.addEventListener('change', () => { field.value = normalizeInput(input, field); setter(field.value); markDirty(); });
    wrap.append(input, el('span', '', field.unit || ''));
    root.append(wrap);
    return root;
  }
  const head = el('div', 'parameter-head');
  head.append(el('strong', '', field.label));
  const wrap = el('div', 'number-wrap');
  const input = document.createElement('input');
  input.type = 'number'; input.min = field.minimum; input.max = field.maximum; input.step = field.step;
  input.value = field.value;
  wrap.append(input, el('span', '', field.unit || ''));
  head.append(wrap);
  const slider = document.createElement('input');
  slider.type = 'range'; slider.min = field.minimum; slider.max = field.maximum; slider.step = field.step;
  slider.value = field.value;
  const commit = (value) => { field.value = value; setter(value); input.value = value; slider.value = value; markDirty(); };
  input.addEventListener('change', () => commit(normalizeInput(input, field)));
  input.addEventListener('input', markDirty);
  slider.addEventListener('input', () => commit(Number(slider.value)));
  const limits = el('div', 'range-limits');
  limits.append(el('span', '', `${field.minimum}`), el('span', '', `${field.maximum}`));
  root.append(head, slider, limits);
  return root;
}

function renderMaterials() {
  const grid = $('#materialGrid'); grid.innerHTML = '';
  const limits = state.materialLimits;
  state.materials.forEach(material => {
    const card = el('article', 'material-card');
    card.style.setProperty('--material-glow', material.glowColor);
    const title = el('div', 'material-title');
    title.append(el('h3', '', material.label));
    const color = document.createElement('input'); color.type = 'color'; color.className = 'color-chip'; color.value = material.glowColor;
    color.title = '灵光与尾迹颜色';
    color.addEventListener('input', () => { material.glowColor = color.value.toUpperCase(); card.style.setProperty('--material-glow', color.value); markDirty(); });
    title.append(color);
    const fields = el('div', 'compact-fields');
    const definitions = [
      { key: 'durability', label: '基础耐久', value: material.durability, minimum: limits.durability[0], maximum: limits.durability[1], step: 1, integer: true },
      { key: 'damage', label: '穿刺伤害', value: material.damage, minimum: limits.damage[0], maximum: limits.damage[1], step: .5, integer: false },
      { key: 'flightSpeed', label: '飞行倍率', value: material.flightSpeed, minimum: limits.flightSpeed[0], maximum: limits.flightSpeed[1], step: .05, integer: false },
      { key: 'glowText', label: '灵光色值', value: material.glowColor, minimum: 0, maximum: 0, step: 0, integer: false },
    ];
    definitions.slice(0, 3).forEach(field => fields.append(createNumericField(field, value => material[field.key] = value, true)));
    const colorField = el('div', 'field'); colorField.append(el('label', 'field-label', '灵光色值'));
    const colorWrap = el('div', 'number-wrap'); const colorText = document.createElement('input'); colorText.value = material.glowColor; colorText.maxLength = 7;
    colorText.addEventListener('change', () => { if (/^#[0-9a-f]{6}$/i.test(colorText.value)) { material.glowColor = colorText.value.toUpperCase(); color.value = material.glowColor; card.style.setProperty('--material-glow', material.glowColor); markDirty(); } else { colorText.value = material.glowColor; toast('颜色格式应为 #RRGGBB', true); } });
    colorWrap.append(colorText); colorField.append(colorWrap); fields.append(colorField);
    card.append(title, fields); grid.append(card);
  });
}

function renderFields(target, fields) {
  const grid = $(target); grid.innerHTML = '';
  fields.forEach(field => grid.append(createNumericField(field, value => field.value = value)));
}

function renderEffects() {
  const container = $('#effectGroups'); container.innerHTML = '';
  const groups = new Map();
  state.effects.forEach(field => { if (!groups.has(field.group)) groups.set(field.group, []); groups.get(field.group).push(field); });
  groups.forEach((fields, key) => {
    const group = el('article', 'effect-group'); group.dataset.search = `${fields[0].groupLabel} ${fields.map(x => x.label).join(' ')}`.toLowerCase();
    group.append(el('h3', '', fields[0].groupLabel));
    const grid = el('div', 'effect-group-grid');
    fields.forEach(field => { const card = createNumericField(field, value => field.value = value); card.className = 'effect-field'; card.dataset.search = field.label.toLowerCase(); grid.append(card); });
    group.append(grid); container.append(group);
  });
}

function renderPresentationDefaults() {
  const descriptions = {
    flightSound: '飞剑高速移动时播放空间化破空声',
    swordTrail: '按材质颜色绘制性能友好的飞行尾迹',
    swordBodyGlow: '飞剑本体使用全亮能量渲染与剑罡',
    inventoryGlint: '物品栏中的飞剑默认显示附魔流光',
    swordEnergyHighlight: '叠加更明亮的能量高光与外层辉光',
    swordOutline: '为飞剑实体启用硬边缘发光轮廓',
    flameModuleVisual: '安装火纹后在剑刃边缘生成克制火星',
    lightningModuleVisual: '安装引雷后偶发短促电弧',
    poisonModuleVisual: '安装蚀毒后在飞行尾迹中混入绿色雾丝',
    explosionModuleVisual: '安装爆裂后沿剑身产生橙红脉冲',
    arrowRainModuleVisual: '安装箭雨后显示细小风纹',
    hitImpactVisual: '命中时显示闪光、冲击环并播放短促声音',
    workbenchPreview: '在飞剑工作台中显示可旋转的真实渲染预览',
  };
  const grid = $('#presentationGrid'); grid.innerHTML = '';
  state.presentationDefaults.forEach(option => {
    const card = document.createElement('button'); card.type = 'button';
    card.className = `presentation-card${option.value ? ' enabled' : ''}`;
    const copy = el('span'); copy.append(el('strong', '', option.label), el('small', '', descriptions[option.key] || option.constant));
    const toggle = el('span', 'presentation-switch'); toggle.setAttribute('aria-hidden', 'true');
    card.setAttribute('aria-pressed', String(option.value));
    card.title = `${option.label}：${option.value ? '默认开启' : '默认关闭'}`;
    card.append(copy, toggle);
    card.addEventListener('click', () => { option.value = !option.value; renderPresentationDefaults(); markDirty(); });
    grid.append(card);
  });
}

function catalogEntry(itemId) {
  return (state.recipeCatalog || []).find(item => item.id === itemId);
}

function catalogLabel(itemId) {
  const entry = catalogEntry(itemId);
  return entry ? entry.label : (itemId.split(':').pop() || itemId).replaceAll('_', ' ');
}

function itemColor(itemId) {
  const colors = {
    diamond: '#63dfda', amethyst: '#be88ef', gold: '#f0cf67', golden: '#f0cf67',
    iron: '#d1d7d8', stone: '#9ca3a2', wooden: '#ba8a58', netherite: '#8b6f79',
    crafting: '#b88b5d', swordflight: '#64e0c2',
  };
  const text = itemId.toLowerCase();
  return Object.entries(colors).find(([key]) => text.includes(key))?.[1] || '#81979a';
}

function itemGlyph(itemId) {
  if (!itemId) return '·';
  if (itemId.includes('sword')) return '剑';
  if (itemId.includes('diamond')) return '◆';
  if (itemId.includes('amethyst')) return '◇';
  if (itemId.includes('crafting_table') || itemId.includes('workbench')) return '台';
  return '灵';
}

function renderRecipeCatalog() {
  const catalog = $('#itemCatalog'); catalog.innerHTML = '';
  (state.recipeCatalog || []).forEach(item => {
    const option = document.createElement('option'); option.value = item.id; option.label = item.label; catalog.append(option);
  });
}

function recipeDisplayName(recipe) {
  return catalogLabel(recipe.result) || recipe.id;
}

function renderRecipeList() {
  const list = $('#recipeList'); list.innerHTML = '';
  const query = $('#recipeSearch').value.trim().toLowerCase();
  state.recipes.forEach((recipe, index) => {
    const searchable = `${recipe.id} ${recipe.result} ${recipeDisplayName(recipe)}`.toLowerCase();
    if (query && !searchable.includes(query)) return;
    const button = document.createElement('button'); button.type = 'button';
    button.classList.toggle('active', index === selectedRecipeIndex);
    button.append(el('span', '', recipeDisplayName(recipe)), el('small', '', recipe.id));
    button.addEventListener('click', () => { selectedRecipeIndex = index; renderRecipeList(); renderRecipeEditor(); });
    list.append(button);
  });
  if (!list.children.length) list.append(el('div', 'muted', query ? '没有匹配的配方' : '暂无配方'));
}

function updateIngredientSummary(recipe) {
  const summary = $('#ingredientSummary'); summary.innerHTML = '';
  const counts = new Map();
  recipe.grid.filter(Boolean).forEach(item => counts.set(item, (counts.get(item) || 0) + 1));
  counts.forEach((count, item) => summary.append(el('span', 'ingredient-chip', `${catalogLabel(item)} × ${count}`)));
  if (!counts.size) summary.append(el('span', 'muted', '请至少放入一种材料'));
}

function updateCraftSlot(slot, itemId) {
  slot.classList.toggle('filled', Boolean(itemId));
  slot.style.setProperty('--slot-color', itemColor(itemId));
  slot.querySelector('.slot-glyph').textContent = itemGlyph(itemId);
}

function renderRecipeEditor() {
  const recipe = state.recipes[selectedRecipeIndex];
  const editor = $('.recipe-editor');
  editor.classList.toggle('hidden', !recipe);
  if (!recipe) return;
  $('#recipeEditorTitle').textContent = recipeDisplayName(recipe);
  $('#recipeId').value = recipe.id;
  $('#recipeType').value = recipe.type;
  $('#recipeCategory').value = recipe.category;
  $('#recipeResult').value = recipe.result;
  $('#recipeCount').value = recipe.count;
  $('#gridModeLabel').textContent = recipe.type === 'shaped' ? '3 × 3 有序合成槽' : '无序材料槽（位置不影响结果）';
  $('#resultGlyph').textContent = itemGlyph(recipe.result);

  const grid = $('#recipeGrid'); grid.innerHTML = '';
  recipe.grid.forEach((itemId, index) => {
    const slot = el('label', 'craft-slot'); slot.dataset.index = index + 1;
    const glyph = el('span', 'slot-glyph', itemGlyph(itemId));
    const input = document.createElement('input'); input.value = itemId; input.setAttribute('list', 'itemCatalog');
    input.placeholder = '留空'; input.spellcheck = false;
    input.addEventListener('input', () => {
      recipe.grid[index] = input.value.trim().toLowerCase(); updateCraftSlot(slot, recipe.grid[index]);
      updateIngredientSummary(recipe); markDirty();
    });
    input.addEventListener('change', () => { renderRecipeList(); });
    slot.append(glyph, input); updateCraftSlot(slot, itemId); grid.append(slot);
  });
  updateIngredientSummary(recipe);
}

function renderRecipes() {
  renderRecipeCatalog(); renderRecipeList(); renderRecipeEditor();
}

function uniqueRecipeId(base) {
  const used = new Set(state.recipes.map(recipe => recipe.id));
  if (!used.has(base)) return base;
  let suffix = 2;
  while (used.has(`${base}_${suffix}`)) suffix += 1;
  return `${base}_${suffix}`;
}

function addRecipe() {
  state.recipes.push({ id: uniqueRecipeId('new_recipe'), type: 'shaped', category: 'misc', result: 'minecraft:diamond', count: 1, grid: Array(9).fill('') });
  selectedRecipeIndex = state.recipes.length - 1; $('#recipeSearch').value = ''; renderRecipes(); markDirty();
}

function cloneRecipe() {
  const source = state.recipes[selectedRecipeIndex]; if (!source) return;
  const clone = structuredClone(source); clone.id = uniqueRecipeId(`${source.id}_copy`);
  state.recipes.splice(selectedRecipeIndex + 1, 0, clone); selectedRecipeIndex += 1; $('#recipeSearch').value = ''; renderRecipes(); markDirty();
}

function deleteRecipe() {
  const recipe = state.recipes[selectedRecipeIndex]; if (!recipe) return;
  if (!window.confirm(`删除配方“${recipe.id}”？保存前仍可通过刷新页面撤销。`)) return;
  state.recipes.splice(selectedRecipeIndex, 1); selectedRecipeIndex = Math.min(selectedRecipeIndex, state.recipes.length - 1);
  renderRecipes(); markDirty();
}

function nextVersion(version, part) {
  let [major, minor, patch] = version.split('.').map(Number);
  if (part === 'major') return `${major + 1}.0.0`;
  if (part === 'minor') return `${major}.${minor + 1}.0`;
  return `${major}.${minor}.${patch + 1}`;
}

function updateVersionPreview() {
  document.querySelectorAll('#versionIncrement button').forEach(button => {
    const next = nextVersion(state.version, button.dataset.value);
    button.querySelector('small').textContent = `${state.version} → ${next}`;
  });
  $('#releaseHint').textContent = `将生成 swordflight-${nextVersion(state.version, increment)}.jar`;
}

function renderRelease() {
  $('#modsDir').value = state.local.modsDir;
  const worlds = $('#worldSelect'); worlds.innerHTML = '';
  if (!state.worlds.length) {
    const option = new Option('未发现可同步的测试世界', ''); option.disabled = true; option.selected = true; worlds.add(option);
    $('#syncWorld').disabled = true; $('#syncOnlyButton').disabled = true;
  } else {
    state.worlds.forEach(world => worlds.add(new Option(world.name, world.path)));
  }
  updateVersionPreview();
}

function valuesPayload() {
  return {
    materials: Object.fromEntries(state.materials.map(item => [item.key, { durability: item.durability, damage: item.damage, flightSpeed: item.flightSpeed, glowColor: item.glowColor }])),
    combat: Object.fromEntries(state.combat.map(item => [item.key, item.value])),
    effects: Object.fromEntries(state.effects.map(item => [item.key, item.value])),
    riding: Object.fromEntries(state.riding.map(item => [item.key, item.value])),
    presentationDefaults: Object.fromEntries(state.presentationDefaults.map(item => [item.key, item.value])),
    recipes: state.recipes,
  };
}

async function loadState() {
  try {
    state = await api(`/api/state?token=${encodeURIComponent(token)}`);
    selectedRecipeIndex = 0;
    $('#version').textContent = state.version;
    $('#connectionText').textContent = '本地后端已连接';
    renderMaterials(); renderFields('#combatGrid', state.combat); renderEffects(); renderFields('#ridingGrid', state.riding); renderPresentationDefaults(); renderRecipes(); renderRelease();
    $('#loading').hidden = true; $('#content').hidden = false; markClean();
  } catch (error) {
    $('#loading').innerHTML = `<strong>无法读取项目：${escapeHtml(error.message)}</strong>`;
    $('#connectionText').textContent = '连接失败';
  }
}

function escapeHtml(text) { const div = document.createElement('div'); div.textContent = text; return div.innerHTML; }

async function saveValues() {
  $('#saveButton').disabled = true;
  try {
    const result = await api('/api/save', { method: 'POST', body: JSON.stringify({ values: valuesPayload(), modsDir: $('#modsDir').value }) });
    state.local.modsDir = $('#modsDir').value; markClean(); toast('源码默认值已安全保存');
  } catch (error) { toast(error.message, true); }
  finally { $('#saveButton').disabled = false; }
}

async function syncWorldOnly() {
  if (!$('#worldSelect').value) return toast('没有可同步的测试世界', true);
  try {
    await api('/api/sync-world', { method: 'POST', body: JSON.stringify({ worldPath: $('#worldSelect').value, values: valuesPayload() }) });
    toast('测试世界材质与效果覆盖值已同步');
  } catch (error) { toast(error.message, true); }
}

async function beginBuild() {
  openDrawer();
  $('#buildTitle').textContent = '正在启动构建'; $('#buildLog').textContent = '提交数值并准备版本……'; $('#buildResult').textContent = '';
  try {
    await api('/api/build', { method: 'POST', body: JSON.stringify({
      values: valuesPayload(), increment, install: $('#installAfterBuild').checked,
      modsDir: $('#modsDir').value, syncWorld: $('#syncWorld').checked, worldPath: $('#worldSelect').value,
    }) });
    markClean(); pollBuild();
  } catch (error) { $('#buildTitle').textContent = '无法开始构建'; $('#buildResult').className = 'drawer-result failed'; $('#buildResult').textContent = error.message; }
}

const phaseProgress = { idle: 0, queued: 7, saving: 16, building: 55, installing: 86, complete: 100, failed: 100 };
const phaseTitle = { queued: '构建已排队', saving: '正在保存与校验', building: 'Gradle 正在铸造版本包', installing: '正在替换测试模组', complete: '构建发布完成', failed: '构建失败' };
async function pollBuild() {
  clearTimeout(pollTimer);
  try {
    const status = await api(`/api/build/status?token=${encodeURIComponent(token)}`);
    $('#buildTitle').textContent = phaseTitle[status.phase] || status.phase;
    $('#buildProgress').style.width = `${phaseProgress[status.phase] ?? 10}%`;
    const log = $('#buildLog'); log.textContent = status.logs.join('\n') || '等待构建日志……'; log.scrollTop = log.scrollHeight;
    if (status.running) pollTimer = setTimeout(pollBuild, 700);
    else if (status.success === true) {
      $('#buildResult').className = 'drawer-result success'; $('#buildResult').textContent = `完成：${status.artifact}`;
      toast(`版本 ${status.version} 已构建并归档`); await loadState();
    } else if (status.success === false) {
      $('#buildResult').className = 'drawer-result failed'; $('#buildResult').textContent = status.error || '构建失败';
    }
  } catch (error) { $('#buildResult').className = 'drawer-result failed'; $('#buildResult').textContent = error.message; }
}

function openDrawer() { $('#buildDrawer').classList.add('open'); $('#buildDrawer').setAttribute('aria-hidden', 'false'); }
function closeDrawer() { $('#buildDrawer').classList.remove('open'); $('#buildDrawer').setAttribute('aria-hidden', 'true'); }

$('#nav').addEventListener('click', event => { const button = event.target.closest('button'); if (!button) return; document.querySelectorAll('#nav button').forEach(x => x.classList.toggle('active', x === button)); document.getElementById(button.dataset.target).scrollIntoView(); });
$('#effectSearch').addEventListener('input', event => { const query = event.target.value.trim().toLowerCase(); document.querySelectorAll('.effect-group').forEach(group => group.classList.toggle('hidden', query && !group.dataset.search.includes(query))); });
$('#recipeSearch').addEventListener('input', renderRecipeList);
$('#addRecipeButton').addEventListener('click', addRecipe);
$('#cloneRecipeButton').addEventListener('click', cloneRecipe);
$('#deleteRecipeButton').addEventListener('click', deleteRecipe);
$('#recipeId').addEventListener('input', event => { const recipe = state.recipes[selectedRecipeIndex]; recipe.id = event.target.value.trim().toLowerCase(); $('#recipeEditorTitle').textContent = recipeDisplayName(recipe); renderRecipeList(); markDirty(); });
$('#recipeType').addEventListener('change', event => { state.recipes[selectedRecipeIndex].type = event.target.value; renderRecipeEditor(); markDirty(); });
$('#recipeCategory').addEventListener('change', event => { state.recipes[selectedRecipeIndex].category = event.target.value; markDirty(); });
$('#recipeResult').addEventListener('input', event => { const recipe = state.recipes[selectedRecipeIndex]; recipe.result = event.target.value.trim().toLowerCase(); $('#recipeEditorTitle').textContent = recipeDisplayName(recipe); $('#resultGlyph').textContent = itemGlyph(recipe.result); renderRecipeList(); markDirty(); });
$('#recipeCount').addEventListener('change', event => { const recipe = state.recipes[selectedRecipeIndex]; const value = Math.round(Number(event.target.value) || 1); recipe.count = Math.max(1, Math.min(64, value)); event.target.value = recipe.count; markDirty(); });
$('#versionIncrement').addEventListener('click', event => { const button = event.target.closest('button'); if (!button) return; increment = button.dataset.value; document.querySelectorAll('#versionIncrement button').forEach(x => x.classList.toggle('active', x === button)); updateVersionPreview(); });
$('#saveButton').addEventListener('click', saveValues); $('#quickBuildButton').addEventListener('click', beginBuild); $('#releaseButton').addEventListener('click', beginBuild); $('#syncOnlyButton').addEventListener('click', syncWorldOnly); $('#closeDrawer').addEventListener('click', closeDrawer);
window.addEventListener('beforeunload', event => { if (dirty) { event.preventDefault(); event.returnValue = ''; } });
loadState();

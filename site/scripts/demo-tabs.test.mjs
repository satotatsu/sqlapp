import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

test('generated detail hash navigation initializes each tab container without global variables', () => {
  const source = fs.readFileSync(new URL('../../sqlapp-command/src/main/resources/com/sqlapp/data/db/command/html/basicDetailLayout.html', import.meta.url), 'utf8').replaceAll('\r\n', '\n');
  const event = source.slice(source.indexOf("$(window).on('hashchange'"), source.indexOf("$('.tabs').each(function(){\n\tvar tab=$(this);"));
  assert.ok(event.length > 0);
  let listener;
  let visited = 0;
  const window = {};
  const container = {
    children(selector) {
      assert.equal(selector, '.nav');
      visited++;
      return { children: () => ({ each() {} }) };
    },
    next: () => ({ 0: true })
  };
  const $ = value => value === window ? { on(name, callback) { assert.equal(name, 'hashchange'); listener = callback; } }
    : value === '.tabs' ? { each(callback) { callback.call(container); } } : value;
  vm.runInNewContext(event, { window, location: { hash: '' }, $ });
  listener();
  assert.equal(visited, 1);
});

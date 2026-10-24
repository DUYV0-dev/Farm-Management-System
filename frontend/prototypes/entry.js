'use strict';
// Direct links enter the authenticated shell; templates are loaded by app.js.
const page = location.pathname.split('/').pop().replace('.html', '');
const target = { dashboard: 'home', workers: 'staff', index: 'tasks' }[page] || page;
location.replace('/#' + target);

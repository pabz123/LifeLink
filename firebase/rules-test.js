const targaryen = require('targaryen'); // setup: npm i targaryen   run: node rules-test.js
const rules = require('./database.rules.json');
const F = 'mbarara-regional-referral-hospital', K = 'kabale-regional-referral-hospital';
const data = {
  requests: { r1: { facility: 'x' } },
  facilities: { [F]: { name: 'M' }, [K]: { name: 'K' } },
  staff: { staffM: { facilityId: F }, staffK: { facilityId: K } },
  pledges: { [F]: { p1: { ownerUid: 'donor1', donorCode: 'K7Q-2M4', plannedDate: '2026-11-20', status: 'PLANNED', createdAt: 1700000000000 } } }
};
const db = targaryen.database(rules, data);
const as = (uid) => uid ? db.as({ uid }) : db.as(null);
const rec = (o = {}) => Object.assign({ ownerUid: 'donor2', donorCode: 'R3D-9X1', plannedDate: '2026-11-22', status: 'PLANNED', createdAt: 1700000000001 }, o);
let pass = 0, fail = 0;
function t(name, cond) { if (cond) pass++; else { fail++; console.log('FAIL', name); } }
const can = (r) => r.allowed === true;

// create
t('donor creates own valid pledge', can(as('donor2').write(`/pledges/${F}/n1`, rec())));
t('create for someone else is refused', !can(as('donor2').write(`/pledges/${F}/n1`, rec({ ownerUid: 'donor1' }))));
t('create already confirmed is refused', !can(as('donor2').write(`/pledges/${F}/n1`, rec({ status: 'CONFIRMED' }))));
t('create in an unknown facility is refused', !can(as('donor2').write(`/pledges/nowhere/n1`, rec())));
t('create without sign-in is refused', !can(as(null).write(`/pledges/${F}/n1`, rec())));
t('create with an extra field is refused', !can(as('donor2').write(`/pledges/${F}/n1`, rec({ name: 'Pabz' }))));
t('create with a phone field is refused', !can(as('donor2').write(`/pledges/${F}/n1`, rec({ phone: '0772' }))));
t('create with a bad code is refused', !can(as('donor2').write(`/pledges/${F}/n1`, rec({ donorCode: 'abc' }))));
t('create with a bad date is refused', !can(as('donor2').write(`/pledges/${F}/n1`, rec({ plannedDate: '20-11-2026' }))));
t('create with a missing field is refused', !can(as('donor2').write(`/pledges/${F}/n1`, { ownerUid: 'donor2', donorCode: 'R3D-9X1', plannedDate: '2026-11-22', status: 'PLANNED' })));
t('create with createdAt as text is refused', !can(as('donor2').write(`/pledges/${F}/n1`, rec({ createdAt: 'now' }))));

// read
t('donor reads own pledge', can(as('donor1').read(`/pledges/${F}/p1`)));
t('another donor cannot read it', !can(as('donor2').read(`/pledges/${F}/p1`)));
t('donor cannot list a facility', !can(as('donor1').read(`/pledges/${F}`)));
t('donor cannot list all pledges', !can(as('donor1').read(`/pledges`)));
t('signed-out cannot read', !can(as(null).read(`/pledges/${F}/p1`)));
t('staff of the facility lists its pledges', can(as('staffM').read(`/pledges/${F}`)));
t('staff of the facility reads one', can(as('staffM').read(`/pledges/${F}/p1`)));
t('staff of another facility cannot list', !can(as('staffK').read(`/pledges/${F}`)));
t('staff of another facility cannot read one', !can(as('staffK').read(`/pledges/${F}/p1`)));
t('staff cannot read every facility', !can(as('staffM').read(`/pledges`)));

// donor changes
t('donor cancels own pledge', can(as('donor1').write(`/pledges/${F}/p1/status`, 'CANCELLED')));
t('donor cannot confirm own pledge', !can(as('donor1').write(`/pledges/${F}/p1/status`, 'CONFIRMED')));
t('donor cannot mark did-not-attend', !can(as('donor1').write(`/pledges/${F}/p1/status`, 'DID_NOT_ATTEND')));
t('retry of the same record is accepted', can(as('donor1').write(`/pledges/${F}/p1`, data.pledges[F].p1)));
t('donor cannot change the planned day', !can(as('donor1').write(`/pledges/${F}/p1/plannedDate`, '2026-12-01')));
t('donor cannot change the owner', !can(as('donor1').write(`/pledges/${F}/p1/ownerUid`, 'donor2')));
t('donor cannot change the code', !can(as('donor1').write(`/pledges/${F}/p1/donorCode`, 'AAA-111')));
t('donor cannot change the created time', !can(as('donor1').write(`/pledges/${F}/p1/createdAt`, 5)));
t('another donor cannot cancel it', !can(as('donor2').write(`/pledges/${F}/p1/status`, 'CANCELLED')));
t('donor deletes own pledge (withdraws)', can(as('donor1').write(`/pledges/${F}/p1`, null)));
t('another donor cannot delete it', !can(as('donor2').write(`/pledges/${F}/p1`, null)));
t('signed-out cannot delete', !can(as(null).write(`/pledges/${F}/p1`, null)));

// staff changes
t('staff confirms', can(as('staffM').write(`/pledges/${F}/p1/status`, 'CONFIRMED')));
t('staff marks did-not-attend', can(as('staffM').write(`/pledges/${F}/p1/status`, 'DID_NOT_ATTEND')));
t('staff cannot cancel for the donor', !can(as('staffM').write(`/pledges/${F}/p1/status`, 'CANCELLED')));
t('staff cannot invent a status', !can(as('staffM').write(`/pledges/${F}/p1/status`, 'HACKED')));
t('staff cannot change the day', !can(as('staffM').write(`/pledges/${F}/p1/plannedDate`, '2026-12-01')));
t('staff cannot change the owner', !can(as('staffM').write(`/pledges/${F}/p1/ownerUid`, 'staffM')));
t('staff cannot delete', !can(as('staffM').write(`/pledges/${F}/p1`, null)));
t('staff of another facility cannot confirm', !can(as('staffK').write(`/pledges/${F}/p1/status`, 'CONFIRMED')));
t('donor cannot confirm even as a staff-looking value', !can(as('donor1').write(`/pledges/${F}/p1`, Object.assign({}, data.pledges[F].p1, { status: 'CONFIRMED' }))));

// after confirmation the donor cannot cancel
const confirmed = db.as({ uid: 'staffM' }).write(`/pledges/${F}/p1/status`, 'CONFIRMED').newDatabase;
t('donor cannot cancel after confirmation', !can(confirmed.as({ uid: 'donor1' }).write(`/pledges/${F}/p1/status`, 'CANCELLED')));
t('donor can read the confirmation', can(confirmed.as({ uid: 'donor1' }).read(`/pledges/${F}/p1/status`)));

// other areas
t('requests readable without sign-in', can(as(null).read('/requests')));
t('requests not writable by staff', !can(as('staffM').write('/requests/r2', { a: 1 })));
t('requests not writable by donors', !can(as('donor1').write('/requests/r2', { a: 1 })));
t('facilities readable', can(as(null).read('/facilities')));
t('facilities not writable', !can(as('staffM').write('/facilities/new', { name: 'x' })));
t('staff entry readable by its owner', can(as('staffM').read('/staff/staffM')));
t('staff entry not readable by others', !can(as('donor1').read('/staff/staffM')));
t('staff list not readable', !can(as('staffM').read('/staff')));
t('staff entry not writable by the donor', !can(as('donor1').write('/staff/donor1', { facilityId: F })));
t('staff entry not writable by staff', !can(as('staffM').write('/staff/staffM', { facilityId: K })));
t('nothing else is readable', !can(as('donor1').read('/')));
t('nothing else is writable', !can(as('donor1').write('/anything', 1)));
console.log(`${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);

const targaryen = require('targaryen'); // setup: npm i targaryen   run: node rules-test.js
const rules = require('./database.rules.json');
const F = 'mbarara-regional-referral-hospital', K = 'kabale-regional-referral-hospital';
const data = {
  requests: { r1: { facility: 'M', facilityId: F, bloodGroup: 'O-', units: 2, urgency: 'URGENT', postedAt: 1700000000000, expiresAt: 1700086400000, verified: true }, legacy: { facility: 'Old', bloodGroup: 'A+', units: 1 } },
  facilities: { [F]: { name: 'M', lat: -0.6, lng: 30.6 }, [K]: { name: 'K', lat: -1.2, lng: 29.9 } },
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
const NOW = Date.now();
const req = (o = {}) => Object.assign({ facility: 'M', facilityId: F, bloodGroup: 'A+', units: 3, urgency: 'CRITICAL', note: 'Urgent', contactPhone: '+256700000000', lat: -0.6, lng: 30.6, verified: true, postedAt: NOW, expiresAt: NOW + 86400000 }, o);
const wr = (uid, path, v) => db.as(uid ? { uid } : null).write(path, v, { now: NOW });
t('staff can post a request for their own facility', can(wr('staffM', '/requests/n1', req())));
t('staff cannot post as another facility', !can(wr('staffM', '/requests/n1', req({ facility: 'K', facilityId: K }))));
t('staff cannot use another facility name', !can(wr('staffM', '/requests/n1', req({ facility: 'K' }))));
t('donors cannot post', !can(wr('donor1', '/requests/n1', req())));
t('signed-out cannot post', !can(wr(null, '/requests/n1', req())));
t('request needs verified true', !can(wr('staffM', '/requests/n1', req({ verified: false }))));
t('bad blood group refused', !can(wr('staffM', '/requests/n1', req({ bloodGroup: 'Z+' }))));
t('units 0 refused', !can(wr('staffM', '/requests/n1', req({ units: 0 }))));
t('units 21 refused', !can(wr('staffM', '/requests/n1', req({ units: 21 }))));
t('units 2.5 refused', !can(wr('staffM', '/requests/n1', req({ units: 2.5 }))));
t('bad urgency refused', !can(wr('staffM', '/requests/n1', req({ urgency: 'NOW' }))));
t('long note refused', !can(wr('staffM', '/requests/n1', req({ note: 'x'.repeat(121) }))));
t('bad phone refused', !can(wr('staffM', '/requests/n1', req({ contactPhone: 'call me' }))));
t('expiry before posting refused', !can(wr('staffM', '/requests/n1', req({ expiresAt: NOW - 1 }))));
t('backdated posting refused', !can(wr('staffM', '/requests/n1', req({ postedAt: NOW - 3600000, expiresAt: NOW + 1000 }))));
t('extra field refused', !can(wr('staffM', '/requests/n1', Object.assign(req(), { hack: 1 }))));
t('staff can extend their own request', can(wr('staffM', '/requests/r1/expiresAt', 1700172800000)));
t('other facility staff cannot extend it', !can(wr('staffK', '/requests/r1/expiresAt', 1700172800000)));
t('donor cannot extend it', !can(wr('donor1', '/requests/r1/expiresAt', 1700172800000)));
t('staff cannot shorten a request to before it was posted', !can(wr('staffM', '/requests/r1/expiresAt', 1600000000000)));
t('staff cannot change the owning facility of a request', !can(wr('staffM', '/requests/r1/facilityId', K)));
t('staff can close their own request', can(wr('staffM', '/requests/r1', null)));
t('other facility staff cannot close it', !can(wr('staffK', '/requests/r1', null)));
t('donor cannot close it', !can(wr('donor1', '/requests/r1', null)));
t('old requests without an owner cannot be closed by staff', !can(wr('staffM', '/requests/legacy', null)));
t('staff cannot move a request to another facility', !can(wr('staffM', '/requests/r1', req({ facility: 'K', facilityId: K, postedAt: 1700000000000 }))));
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

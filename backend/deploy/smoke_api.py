"""Live HTTPS API verification. Creates identifiable demo records; never prints credentials."""
import argparse
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', required=True)
parser.add_argument('--origin', default='http://localhost:5173')
args = parser.parse_args()
base = args.base_url.rstrip('/')
assert urllib.parse.urlsplit(base).scheme == 'https', 'Use HTTPS for authenticated smoke tests'
jar = http.cookiejar.CookieJar()
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
checks = []

def passed(message):
    checks.append(message)
    print('PASS ' + message, flush=True)

def request(path, method='GET', data=None, form=False, expected=200, csrf=True, origin=None, opener=None):
    headers = {'Origin': origin or args.origin}
    if method not in ['GET', 'HEAD', 'OPTIONS'] and csrf:
        token, _ = request('/api/auth/csrf')
        headers[token['headerName']] = token['token']
    body = None
    if data is not None:
        body = (urllib.parse.urlencode(data) if form else json.dumps(data)).encode()
        headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
    if method == 'OPTIONS':
        headers.update({'Access-Control-Request-Method': 'PUT', 'Access-Control-Request-Headers': 'content-type,x-csrf-token'})
    try:
        response = (opener or client).open(urllib.request.Request(base + path, data=body, method=method, headers=headers), timeout=90)
    except urllib.error.HTTPError as error:
        response = error
    raw = response.read()
    assert response.status == expected, f'{method} {path.split("?")[0]}: expected {expected}, got {response.status}'
    if response.status != 403:
        assert response.headers.get('Access-Control-Allow-Origin') == args.origin, 'Missing/excess CORS origin'
        assert response.headers.get('Access-Control-Allow-Credentials') == 'true'
    assert response.headers.get('Age') in (None, '0'), 'Response unexpectedly cached'
    payload = json.loads(raw) if raw and 'application/json' in response.headers.get('Content-Type', '') else None
    return payload, response.headers

# Health is outside /api and intentionally has no CORS policy.
with urllib.request.urlopen(base + '/actuator/health', timeout=30) as response:
    assert json.load(response) == {'status': 'UP'}
passed('HTTPS certificate and Aiven-backed health')

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

try:
    urllib.request.build_opener(NoRedirect).open(base.replace('https://', 'http://', 1) + '/actuator/health', timeout=30)
    raise AssertionError('HTTP did not redirect')
except urllib.error.HTTPError as error:
    assert error.code in (301, 302, 307, 308)
    assert error.headers['Location'].startswith(base)
passed('HTTP redirects to HTTPS')

request('/api/portfolios/1/allocations', 'OPTIONS')
request('/api/portfolios/1/allocations', 'OPTIONS', origin='https://untrusted.example.com', expected=403)
passed('credentialed CORS preflight and rejected untrusted origin')
_, headers = request('/api/auth/csrf')
cookie = headers.get('Set-Cookie', '').lower()
assert 'secure' in cookie and 'httponly' in cookie and 'samesite=none' in cookie
passed('Set-Cookie forwards Secure, HttpOnly, SameSite=None')
stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
email = f'cloudfront-smoke-{stamp.lower()}-{secrets.token_hex(3)}@example.com'
password = secrets.token_urlsafe(24)
request('/api/auth/register', 'POST', {'email': email, 'password': password}, expected=201)
passed('registration JSON body')

def login():
    # Email travels in the query string; password stays in the POST body.
    request('/api/auth/login?' + urllib.parse.urlencode({'email': email}), 'POST', {'password': password}, form=True, expected=204)
    identity, _ = request('/api/auth/me')
    assert identity['email'] == email

login()
passed('login query parameters, form body and session-cookie forwarding')
portfolio, _ = request('/api/portfolios', 'POST', {'name': 'CloudFront smoke ' + stamp}, expected=201)
pid = portfolio['id']
path = f'/api/portfolios/{pid}'
saved, _ = request(path)
assert saved['name'] == portfolio['name']
passed('create and fetch portfolio')
empty, _ = request(path + '/analytics')
assert float(empty['totalMarketValue']) == 0
passed('empty portfolio analytics')
tx, _ = request(path + '/transactions', 'POST', {'symbol': 'AAPL', 'type': 'BUY', 'quantity': 10, 'price': 150}, expected=201)
history, _ = request(path + '/transactions')
assert len(history) == 1 and history[0]['id'] == tx['id']
assert float(history[0]['quantity']) == 10 and float(history[0]['price']) == 150
holdings, _ = request(path + '/holdings')
assert len(holdings) == 1 and holdings[0]['symbol'] == 'AAPL' and float(holdings[0]['quantity']) == 10
passed('transaction JSON persistence, history and derived holdings')
request(path + '/allocations', 'PUT', {'targets': [{'symbol': 'AAPL', 'targetPercent': 100}]})
targets, _ = request(path + '/allocations')
assert len(targets) == 1 and float(targets[0]['targetPercent']) == 100
request(path + '/allocations', 'PUT', {'targets': [{'symbol': 'AAPL', 'targetPercent': 50}]}, expected=400)
passed('PUT target allocation and rejection of invalid totals')
analytics_error = None
try:
    summary, _ = request(path + '/analytics')
    assert float(summary['totalMarketValue']) > 0
    drift, _ = request(path + '/allocations/drift')
    assert any(row['symbol'] == 'AAPL' for row in drift)
    passed('live market valuation, analytics and allocation drift')
except AssertionError as error:
    analytics_error = str(error)
    print('FAILED market-dependent analytics: ' + analytics_error, flush=True)

anonymous = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
request('/api/auth/me', expected=401, opener=anonymous)
request('/api/auth/logout', 'POST', expected=204)
request('/api/auth/me', expected=401)
passed('anonymous/session isolation and logout invalidation without cached identity replay')
login()
assert request(path)[0]['id'] == pid
assert len(request(path + '/transactions')[0]) == 1
assert float(request(path + '/holdings')[0][0]['quantity']) == 10
assert float(request(path + '/allocations')[0][0]['targetPercent']) == 100
passed('persisted portfolio, transaction and allocation after a fresh login')
request('/api/auth/logout', 'POST', expected=204)
output = Path(__file__).resolve().parents[1] / 'target'
output.mkdir(exist_ok=True)
report = {'baseUrl': base, 'origin': args.origin, 'portfolioId': pid, 'transactionId': tx['id'], 'email': email, 'checks': checks, 'analyticsError': analytics_error}
(output / 'cloudfront-smoke-report.json').write_text(json.dumps(report, indent=2))
# Optional subsequent browser/database verification can reuse this dedicated account.
fd = os.open(output / 'cloudfront-smoke-credentials.json', os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
os.fchmod(fd, 0o600)
with os.fdopen(fd, 'w') as secret_file:
    json.dump({'email': email, 'password': password, 'portfolioId': pid}, secret_file)
print(f'Demo records retained: portfolio {pid}; session logged out.', flush=True)
if analytics_error:
    raise SystemExit(1)

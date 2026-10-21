#!/usr/bin/env bash
# Reusable Linux cloud setup. Keep downloads and machine-specific configuration out of Git.
set -euo pipefail
portal_repo="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
portal_state="${PORTAL_CLOUD_STATE:-$portal_repo/.cloud}"
mkdir -p "$portal_state"
if [[ -n "${JAVA_HOME:-}" ]] && [[ "$("$JAVA_HOME/bin/javac" -version 2>&1)" == 'javac 25.'* ]]; then
    portal_jdk="$JAVA_HOME"
else
    portal_jdk="$portal_state/jdk-25"
    if [[ ! -x "$portal_jdk/bin/javac" ]]; then
        portal_archive="$portal_state/jdk-25.tar.gz"
        portal_url='https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz'
        curl --fail --location --retry 2 "$portal_url.sha256" --output "$portal_archive.sha256"
        curl --fail --location --retry 2 "$portal_url" --output "$portal_archive"
        python3 - "$portal_archive" <<'PY'
import hashlib, pathlib, sys
archive = pathlib.Path(sys.argv[1])
expected = pathlib.Path(str(archive) + '.sha256').read_text().split()[0]
with archive.open('rb') as stream:
    if hashlib.file_digest(stream, 'sha256').hexdigest() != expected:
        raise SystemExit('JDK archive SHA-256 mismatch')
PY
        portal_unpack="$(mktemp -d "$portal_state/unpack.XXXXXXXX")"
        tar -xzf "$portal_archive" -C "$portal_unpack"
        portal_candidates=("$portal_unpack"/jdk-25*)
        [[ ${#portal_candidates[@]} == 1 && -x "${portal_candidates[0]}/bin/javac" ]]
        mv "${portal_candidates[0]}" "$portal_jdk"
        rmdir "$portal_unpack"
    fi
fi
export JAVA_HOME="$portal_jdk"
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$portal_state/gradle}"
mkdir -p "$GRADLE_USER_HOME"
export PORTAL_SETUP_STATE="$portal_state"
python3 - <<'PY'
import os, pathlib, shlex
from urllib.parse import urlparse

cache = pathlib.Path(os.environ['GRADLE_USER_HOME'])
props = cache / 'gradle.properties'
begin, end = '# BEGIN immersive-portals cloud setup', '# END immersive-portals cloud setup'
current = props.read_text() if props.exists() else ''
if begin in current:
    before, rest = current.split(begin, 1)
    _, after = rest.split(end, 1)
    current = before.rstrip() + after
lines = [begin, 'org.gradle.workers.max=2']
ca = pathlib.Path('/etc/ssl/certs/java/cacerts')
if ca.is_file():
    lines += [f'systemProp.javax.net.ssl.trustStore={ca}']
proxy = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy')
if proxy:
    parsed = urlparse(proxy)
    if parsed.hostname and not parsed.username and not parsed.password:
        for scheme in ('https', 'http'):
            lines += [f'systemProp.{scheme}.proxyHost={parsed.hostname}',
                      f'systemProp.{scheme}.proxyPort={parsed.port or 80}']
        lines += ['systemProp.http.nonProxyHosts=localhost|127.*|[::1]']
    else:
        print('Configure the authenticated Java proxy separately; credentials were not saved.')
props.write_text(current.rstrip() + '\n' + '\n'.join(lines + [end]) + '\n')
state = pathlib.Path(os.environ['PORTAL_SETUP_STATE'])
activation = ''.join(f'export {name}={shlex.quote(os.environ[name])}\n'
                     for name in ('JAVA_HOME', 'GRADLE_USER_HOME'))
activation += 'export PATH="$JAVA_HOME/bin:$PATH"\n'
(state / 'activate.sh').write_text(activation)
PY
cd "$portal_repo"
"$JAVA_HOME/bin/javac" -version
if [[ "${1:-}" != '--prepare-only' ]]; then
    ./gradlew build validateAccessWidener --console=plain
fi
printf 'Activation: source %q/activate.sh\n' "$portal_state"

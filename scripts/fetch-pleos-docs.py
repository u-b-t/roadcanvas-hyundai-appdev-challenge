#!/usr/bin/env python3
"""Download the public Korean Pleos Connect guides listed in its sitemap."""
from concurrent.futures import ThreadPoolExecutor, as_completed
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen
import time
import xml.etree.ElementTree as ET

BASE = 'https://document.pleos.ai'
DEST = Path('docs/pleos')


class Article(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.depth = 0
        self.parts = []

    def handle_starttag(self, tag, attrs):
        if tag == 'article' and not self.depth:
            self.depth = 1
        elif self.depth:
            if tag not in ('area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'param', 'source', 'track', 'wbr'):
                self.depth += 1
            if tag in ('p', 'div', 'li', 'pre', 'h1', 'h2', 'h3', 'h4', 'tr', 'br'):
                self.parts.append('\n')

    def handle_endtag(self, tag):
        if self.depth:
            self.depth -= 1
            if tag in ('p', 'li', 'pre', 'h1', 'h2', 'h3', 'h4', 'tr'):
                self.parts.append('\n')

    def handle_data(self, data):
        if self.depth:
            self.parts.append(data)


def get(url):
    with urlopen(Request(url, headers={'User-Agent': 'Roadcanvas documentation research contact: developer'}), timeout=40) as response:
        return response.read()


def fetch(path):
    url = BASE + path
    for attempt in range(3):
        try:
            parser = Article()
            parser.feed(get(url).decode('utf-8'))
            body = '\n'.join(line.strip() for line in ''.join(parser.parts).splitlines() if line.strip())
            if not body:
                raise ValueError('no article content')
            target = DEST / (path.strip('/') + ('' if not path.endswith('/') else '/index') + '.txt')
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text('출처: ' + url + '\n\n' + body + '\n', encoding='utf-8')
            return path, None
        except Exception as exc:
            if attempt == 2:
                return path, str(exc)
            time.sleep(2 ** attempt)


root = ET.fromstring(get(BASE + '/sitemap.xml'))
paths = sorted({urlparse(node.text).path for node in root.iter() if node.tag.endswith('loc') and node.text})
paths = [p for p in paths if p.startswith('/docs/pleos-only/') and '/in-prep/' not in p and ('/connect-sdk-pleos/' not in p or len(p.strip('/').split('/')) <= 5)]
# The sitemap currently lists this removed page, which returns an HTML 404.
paths = [p for p in paths if p != '/docs/pleos-only/infolink-emulator/setup-infolink-emulator']
print(f'Fetching {len(paths)} Korean guide pages', flush=True)
errors = []
with ThreadPoolExecutor(max_workers=4) as pool:
    for i, future in enumerate(as_completed([pool.submit(fetch, p) for p in paths]), 1):
        path, error = future.result()
        if error:
            errors.append((path, error))
        if i % 20 == 0 or i == len(paths):
            print(f'{i}/{len(paths)} complete; {len(errors)} errors', flush=True)
if errors:
    for path, error in errors:
        print(f'FAILED {path}: {error}', flush=True)
    raise SystemExit(1)

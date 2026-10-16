#!/usr/bin/env python3
"""Apply Parchment's globally unique MCP parameter IDs to existing Java sources.

Unlike extractMapped/setup, this preserves the workspace's patched implementation.
Only Java identifiers are replaced; strings, character literals and comments stay
unchanged. Parchment does not supply arbitrary decompiler local-variable names.
"""

import argparse
import csv
import io
from pathlib import Path
import re
import zipfile


TOKENS = re.compile(
    r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\''
    r'|//[^\r\n]*|/\*[\s\S]*?\*/|[A-Za-z_$][A-Za-z0-9_$]*'
)
PARAMETER = re.compile(r'p_\d+_')
IDENTIFIER = re.compile(r'[A-Za-z_$][A-Za-z0-9_$]*')


def load_names(archive):
    names = {}
    with zipfile.ZipFile(archive) as mappings:
        with mappings.open('params.csv') as params:
            for row in csv.DictReader(io.TextIOWrapper(params, encoding='utf-8')):
                source, target = row['searge'], row['name']
                if not PARAMETER.fullmatch(source):
                    continue
                if not IDENTIFIER.fullmatch(target):
                    raise ValueError(f'Invalid Java parameter name: {target!r}')
                if source in names and names[source] != target:
                    raise ValueError(f'Ambiguous mapping for {source}')
                names[source] = target
    return names


def rename(source, names):
    return TOKENS.sub(lambda match: names.get(match[0], match[0]), source)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mappings', type=Path, help='Librarian mappings ZIP containing params.csv')
    parser.add_argument('sources', type=Path, nargs='+', help='Java source directories')
    parser.add_argument('--check', action='store_true', help='Report pending changes without writing')
    args = parser.parse_args()
    names = load_names(args.mappings)
    changed = 0
    for root in args.sources:
        if not root.is_dir():
            parser.error(f'Source directory does not exist: {root}')
        for path in sorted(root.rglob('*.java')):
            original = path.read_bytes().decode('utf-8')
            updated = rename(original, names)
            if updated != original:
                changed += 1
                if not args.check:
                    path.write_bytes(updated.encode('utf-8'))
    print(f'{changed} Java files {"need updating" if args.check else "updated"}')
    return int(args.check and changed != 0)


if __name__ == '__main__':
    raise SystemExit(main())

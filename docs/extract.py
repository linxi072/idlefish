import zipfile, re, os, glob

def extract_docx(path):
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        main = 'word/document.xml'
        if main not in names:
            # fallback: find largest document xml
            candidates = [n for n in names if n.endswith('.xml') and 'document' in n]
            if not candidates:
                return '(no document.xml found)'
            main = candidates[0]
        xml = z.read(main).decode('utf-8')
    paras = re.split(r'</w:p>', xml)
    out = []
    for p in paras:
        texts = re.findall(r'<w:t[^>]*>(.*?)</w:t>', p, re.S)
        line = ''.join(texts)
        line = (line.replace('&amp;', '&').replace('&lt;', '<')
                    .replace('&gt;', '>').replace('&quot;', '"')
                    .replace('&#39;', "'").replace('&apos;', "'"))
        out.append(line)
    return '\n'.join(out)

base = os.path.dirname(os.path.abspath(__file__))
for path in sorted(glob.glob(os.path.join(base, '*.docx'))):
    txt = extract_docx(path)
    out_path = path[:-5] + '.txt'
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(txt)
    print('EXTRACTED', os.path.basename(out_path), len(txt), 'chars')

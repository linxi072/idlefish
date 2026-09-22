import zipfile, re, os, glob

def clean(path):
    with zipfile.ZipFile(path) as z:
        xml = z.read('word/document.xml').decode('utf-8')
    # 段落/表格换行与单元格分隔
    xml = xml.replace('</w:p>', '\n').replace('</w:tr>', '\n').replace('</w:tc>', ' | ')
    # 保留 w:t 文本，其后补一个空格
    xml = re.sub(r'<w:t[^>]*>(.*?)</w:t>',
                 lambda m: m.group(1).replace('\n', '') + ' ', xml, flags=re.S)
    # 移除其余所有 XML 标签
    xml = re.sub(r'<[^>]+>', '', xml)
    xml = (xml.replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>')
              .replace('&quot;', '"').replace('&#39;', "'").replace('&apos;', "'"))
    xml = re.sub(r'[ \t]+\n', '\n', xml)
    xml = re.sub(r'\n\s*\n+', '\n', xml)
    return xml

base = os.path.dirname(os.path.abspath(__file__))
for path in sorted(glob.glob(os.path.join(base, '*.docx'))):
    txt = clean(path)
    out = path[:-5] + '_clean.txt'
    with open(out, 'w', encoding='utf-8') as f:
        f.write(txt)
    print('CLEAN', os.path.basename(out), len(txt), 'chars')

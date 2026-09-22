import urllib.request, os

base = os.path.dirname(os.path.abspath(__file__))
names = ['系统架构说明书.docx', '页面原型图V1.0.html', 'PRD首版全文.docx', 'P0研发任务拆解.docx']

with open(os.path.join(base, 'urls.txt'), 'r', encoding='utf-8') as f:
    urls = [l.strip() for l in f if l.strip()]

for name, url in zip(names, urls):
    u = url.replace('\\u0026', '&')
    out = os.path.join(base, name)
    try:
        urllib.request.urlretrieve(u, out)
        print('OK', name, os.path.getsize(out), 'bytes')
    except Exception as e:
        print('FAIL', name, repr(e))

"""Generate search data from the Java page declarations, never from a second feature list.

Runs before javac. Only literal UI arguments and constants are evaluated; no Java is executed.
"""
import pathlib, re, json, sys, hashlib
root = pathlib.Path(sys.argv[1] if len(sys.argv)>1 else '.')
src = root/'src/io/github/pixelsuite'
files = {p.stem:p.read_text(encoding='utf-8-sig') for p in src.glob('*.java')}
lit = r'"(?:\\.|[^"\\])*"'
def string(s):
    try: return json.loads(s)
    except Exception: return None
constants={}
for name,s in files.items():
    for declaration in re.finditer(r'\bString\s+([^;\n]+);',s):
        for m in re.finditer(r'(\w+)\s*=\s*('+lit+r')',declaration[1]):
            constants[name+'.'+m[1]]=string(m[2])
def value(s):
    s=s.strip()
    if re.fullmatch(lit,s): return string(s)
    return constants.get(s)
def end_at(s,start,opening='(',closing=')'):
    depth=0; quoted=False; escape=False
    for i in range(start,len(s)):
        c=s[i]
        if quoted:
            if escape: escape=False
            elif c=='\\': escape=True
            elif c=='"': quoted=False
        elif c=='"': quoted=True
        elif c==opening: depth+=1
        elif c==closing:
            depth-=1
            if depth==0:return i
    return len(s)-1
def args(s):
    out=[]; start=0; depth=0; quoted=False; escape=False
    for i,c in enumerate(s):
        if quoted:
            if escape:escape=False
            elif c=='\\':escape=True
            elif c=='"':quoted=False
        elif c=='"':quoted=True
        elif c in '({[':depth+=1
        elif c in ')}]':depth-=1
        elif c==',' and depth==0:out.append(s[start:i]);start=i+1
    return out+[s[start:]]
def calls(s,names):
    for m in re.finditer(r'\b('+names+r')\s*\(',s):
        start=s.index('(',m.start()); end=end_at(s,start)
        yield m.start(),m[1],args(s[start+1:end]),s[start+1:end]
s=files['SettingsActivity']
titles={'':'Pixel Suite'}; subtitles={}
for method,target in [('pageTitle',titles),('pageSubtitle',subtitles)]:
    pos=s.find('String '+method+'('); brace=s.find('{',pos); body=s[brace:end_at(s,brace,'{','}')]
    for m in re.finditer(r'if\(('+lit+r')\.equals\(id\)\)return\s*('+lit+r')',body):target[string(m[1])]=string(m[2])
entries=[]; seen=set();parents={}
def clean(text):
    for _ in range(2):
        if not any(x in text for x in ('Ã','Â','â','ð')):break
        changed=False
        for encoding in ('cp1252','latin1'):
            try:
                fixed=text.encode(encoding).decode('utf-8')
                if fixed!=text:text=fixed;changed=True;break
            except (UnicodeError,LookupError):pass
        if not changed:break
    return re.sub(r'\*\*','',text)
def add(page,title,description='',kind='TEXT',key='',icon='ic_ps_info',anchor=None):
    if not title or not page in titles:return
    original=title;title=clean(re.sub(r'<[^>]+>','',title).strip())
    if not title:return
    if title.casefold()=='lsposed scope':return
    identity=(page,title,kind,key)
    if identity in seen:return
    seen.add(identity)
    entries.append(dict(id=hashlib.sha1(repr(identity).encode()).hexdigest()[:16],page=page,title=title,
        description=clean(description or ''),kind=kind,key=key or '',icon=icon or 'ic_ps_info',
        breadcrumb=titles[page],anchor=anchor or original,keywords=' '.join(re.findall(r'[a-z]+',key or ''))))
features=[]
for pos,name,a,raw in calls(s,'FeaturePage'):
    if len(a)!=5:continue
    vals=[value(x) for x in a]
    if all(x is not None for x in vals):
        parent,key,icon,title,desc=vals; titles[key]=title;subtitles[key]=desc;features.append(vals);parents[key]=parent
for page,title in titles.items():add(page,title,subtitles.get(page,''),'PAGE')
for parent,key,icon,title,desc in features:
    add(key,title,desc,'TOGGLE',key,icon,anchor=key)
# Assign declarations to their enclosing page branch, including nested feature branches.
start=s.index('private void buildPage('); brace=s.index('{',start); finish=end_at(s,brace,'{','}'); body=s[brace:finish]
ranges=[]
for m in re.finditer(r'if\s*\(([^\n;{}]*)\)\s*\{',body):
    condition=m[1]; pages=[]
    for token in re.finditer(r'('+lit+r'|\w+\.\w+)\.equals\((id|f\.key)\)',condition):
        v=value(token[1]);
        if v in titles:pages.append(v)
    if pages:
        b=body.index('{',m.start());ranges.append((b,end_at(body,b,'{','}'),pages))
for m in re.finditer(r'if\s*\(([^\n;{}]*)\)\s*(?!\{)(explanation|section|choice)\s*\(',body):
    pages=[value(t[1]) for t in re.finditer(r'('+lit+r'|\w+\.\w+)\.equals\((id|f\.key)\)',m[1]) if value(t[1]) in titles]
    if pages:ranges.append((m.end(1),body.find(';',m.end()),pages))
def pages_at(pos):
    hits=[r for r in ranges if r[0]<pos<r[1]]
    return min(hits,key=lambda r:r[1]-r[0])[2] if hits else ['']
for pos,name,a,raw in calls(body,'row|navigation|appNavigation|explanation|section|choice|navChoice|labelSize|surfaceStyle|chromeGuidePart'):
    pages=pages_at(pos)
    if name=='row' and len(a)>=6:
        key,icon,title,desc=value(a[1]),value(a[2]),value(a[4]),value(a[5])
        if key and title:
            for page in pages:add(page,title,desc,'TOGGLE',key,icon,key)
    elif name=='navigation' and len(a)>=5:
        title,desc,icon=value(a[1]),value(a[2]),value(a[3]); target=re.search(r'openPage\(('+lit+r')\)',raw)
        if target and len(pages)==1 and pages[0]!=string(target[1]):parents[string(target[1])]=pages[0]
        for page in ([string(target[1])] if target else pages):add(page,title,desc,'ACTION',icon=icon)
    elif name=='appNavigation' and len(a)>=7:
        page=value(a[6]);parents[page]=pages[0];add(page,value(a[1]),value(a[2]),'PAGE',icon=value(a[5]))
    elif name=='chromeGuidePart' and len(a)>2 and value(a[1]) and value(a[2]):
        # r262: the "How it works" parts (Chrome+, Gboard+) are found by their title and text
        for page in pages:add(page,value(a[1]),value(a[2]),anchor=value(a[1]))
    elif name in ('explanation','section') and len(a)>1 and value(a[1]):
        text=value(a[1])
        for page in pages:add(page,text if name=='section' else text.split('\n')[0][:90],text,anchor=text)
    elif name in ('choice','navChoice') and len(a)>2:
        for page in pages:add(page,value(a[2]),' '.join(filter(None,(string(x) for x in re.findall(lit,raw)))),'CHOICE',value(a[1]),icon='ic_ps_toast' if value(a[1])==constants.get('SystemToasts.KEY') else 'ic_ps_info',anchor=value(a[2]) if name=='navChoice' else None)
    elif name=='surfaceStyle':
        style_source=s[s.index('private void surfaceStyle('):s.index('private void labelSize(')]
        add('themes','Surfaces and blur','play integrity google play services '+ ' '.join(string(x) for x in re.findall(lit,style_source)),'CHOICE',constants.get('SurfaceStyle.KEY'),anchor='Surfaces and blur')
    elif name=='labelSize':
        for page in pages:add(page,'Text size','Icon label size smallest small medium large largest','CHOICE',anchor='Text size')
def ui_strings(source):
    texts=[]
    for _,_,a,_ in calls(source,'text|button|setTitle|setMessage|navigation|explanation|section|choice|setText|setHint|setContentDescription|row'):
        for argument in a:
            text=value(argument)
            if text and not text.startswith(('ic_ps_','pixelsuite_','http','com.')):texts.append(text)
    return list(dict.fromkeys(texts))
# Custom page components contribute their own UI strings, associated where constructed.
for m in re.finditer(r'new\s+(\w+)\(',body):
    name=m[1]
    if name not in files or name in ('FeaturePage','Runnable'):continue
    texts=ui_strings(files[name])
    for page in pages_at(m.start()):
        if texts:add(page,titles[page], '\n'.join(dict.fromkeys(texts)))
# Shared component strings are obtained from the actual invocation sites too.
for name in ('PatronPaper','PatronAccount','EsimPurchase'):
    if name not in files:continue
    text=' '.join(ui_strings(files[name]))
    add('pixel_watch' if name=='EsimPurchase' else 'google_apps',name.replace('PatronPaper','Read the quest').replace('PatronAccount','Patreon account').replace('EsimPurchase','Pixel watch eSIM purchase'),text,'ACTION',anchor='@'+name)
# Helper methods used by a page contribute their UI strings to that page as well.
for m in re.finditer(r'\b(buildUpdates|communityActions)\(page\)',body):
    name=m[1];definition=re.search(r'private void '+name+r'\([^)]*\)\s*\{',s)
    if definition:
        b=s.index('{',definition.start());text=' '.join(ui_strings(s[b:end_at(s,b,'{','}')]))
        for page in pages_at(m.start()):add(page,titles[page],text)
for p in (root/'res/raw').glob('*changelog*'):
    try:add('changelog','Changelog',p.read_text(encoding='utf-8-sig'))
    except UnicodeError:pass
# LSPosed setup stays on its settings pages; it is not a search result or a source of keywords.
out=root/'res/raw/app_search.json';out.parent.mkdir(parents=True,exist_ok=True)
for entry in entries:
    if entry.get('anchor') == '@PatronAccount':
        entry['keywords'] = (entry.get('keywords', '') + ' patreon sign in unlock').strip()
    if entry.get('page') == 'chrome':
        entry['keywords'] = (entry.get('keywords', '') + ' filtering level per site remove element chrome ad blocker button bottom bar opera navigation bar address bar chrome layout speed dial shortcuts new tab page extensions extension menu pin to toolbar animation transition smooth motion animated icons reorder drag move shortcut add shortcut folder folders speed dial folder group shortcuts rename folder speed dial bookmarks bookmark folder round icons circle icons no suggestions suggested sites most visited super dark chrome+ chrome plus vpn extension vpn for chrome country pin extension pinned extension dark pages dark websites').strip()
    if entry.get('page') == 'ad_blocker':
        entry['keywords'] = (entry.get('keywords', '') + ' Ad Blocker allowed websites whitelist filtering level default basic optimal complete per-site levels removed elements restore reset site settings').strip()
    if entry.get('page') == 'dark_pages':
        entry['keywords'] = (entry.get('keywords', '') + ' dark mode dark theme night mode dark websites dark web pages black pages super dark pure black schedule timer sunset night automatic chrome extension').strip()
    if entry.get('page') == 'vpn':
        entry['keywords'] = (entry.get('keywords', '') + ' vpn proxy change country location ip address united states usa united kingdom uk britain japan korea germany vpn gate open source open-source softether quick settings tile patreon chrome only vpn for chrome location gps geolocation google location time zone timezone travel').strip()
    if entry.get('page') == 'google_dialer':
        entry['keywords'] = (entry.get('keywords', '') + ' recording quality hd recording lossless wav call quality').strip()
    page=entry['page'];trail=[];visited=set()
    while page and page not in visited:
        visited.add(page);trail.insert(0,titles.get(page,page));page=parents.get(page,'')
    entry['breadcrumb']=' › '.join(trail) or 'Pixel Suite'
out.write_text(json.dumps(entries,ensure_ascii=False),encoding='utf-8')
print('Search catalog:',len(entries),'entries from page declarations')

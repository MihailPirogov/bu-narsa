import json,re,csv,unicodedata,collections
ROOT='/workspace/tatar-kupu/'
base=json.load(open('mhr_dictionary.orig.json'))['entries']
SCENE={l.strip().lower() for l in open(ROOT+'dictionary/scene_exclude.txt') if l.strip()}
WD=json.load(open('wikidata.json'))
def norm(s): return unicodedata.normalize('NFC',''.join(c for c in unicodedata.normalize('NFD',s) if c!='\u0301')).lower().replace('ё','е').strip()
CYR=re.compile(r'^[а-яёәөүҗңһ\- ]+$')
BAD=re.compile(r'^(разг|диал|устар|уст|перен|прост|поэт|книжн|ласк|уменьш|бран|груб|ирон|шутл|ист|рел|редк|старин|арх)\.?$')
ABBR=re.compile(r'^((?:[а-яё]+\.\s*)+)')
ru=collections.defaultdict(list); wk={}
for l in open('/workspace/tatar-wow/src/ru_tt.jsonl'):
    o=json.loads(l)
    if o.get('pos')!='noun' or ' ' in o['word'].strip() and len(o['word'].split())>2: continue
    w=unicodedata.normalize('NFC',o['word'])
    if not CYR.match(w.lower()): continue
    for si,s in enumerate(o.get('senses',[])):
        for g in s.get('glosses',[]):
            m=ABBR.match(g); labs=m.group(1).split() if m else []; g2=g[len(m.group(1)):] if m else g
            bad=any(BAD.match(x) for x in labs)
            for pi,p in enumerate(re.split(r'[;,]',re.sub(r'\(.*?\)','',g2))):
                p=norm(p)
                if p: ru[p].append((bad,si,pi,w))
    wk.setdefault(w,o)
en=collections.defaultdict(list)
for l in open('/workspace/tatar-wow/src/en_tt.jsonl'):
    o=json.loads(l)
    if o.get('pos')!='noun': continue
    w=unicodedata.normalize('NFC',o['word'])
    if not CYR.match(w.lower()): continue
    for si,s in enumerate(o.get('senses',[])):
        for g in s.get('glosses',[]):
            for pi,p in enumerate(re.split(r'[;,]',re.sub(r'\(.*?\)','',g))):
                p=p.strip().lower()
                if p: en[p].append((si,pi,w))
def wdtt(r):
    e=WD.get(r[:1].upper()+r[1:])
    if not e: return None,None
    for v in (e.get('tt'),e.get('ttwiki')):
        if v:
            v=norm(re.sub(r'\s*\(.*?\)','',v))
            if CYR.match(v) and len(v.split())<=3: return v,e['qid']
    return None,e['qid']
def mkdef(w):
    o=wk.get(w)
    if not o: return None
    ss=[]
    for s in o.get('senses',[])[:4]:
        g='; '.join(s.get('glosses',[]))
        if not g: continue
        m=ABBR.match(g); lab=m.group(1).strip() if m else ''; g=g[len(m.group(1)):] if m else g
        ex=[{'tt':x['text'],'ru':x.get('translation','')} for x in s.get('examples',[])[:2] if x.get('text')]
        ss.append({'d':g.strip(),'lab':lab,'ex':ex})
    if not ss: return None
    return {'stress':'','senses':ss,'source':'Викисловарь (ru.wiktionary.org), CC BY-SA 4.0 · статья «%s»'%w}
OV={}
try:
    for r in csv.DictReader(open('overrides_tt.csv',encoding='utf-8')): OV[r['label'].lower()]=r
except FileNotFoundError: pass
out={};rows=[]
for k,v in base.items():
    r=v.get('ru',''); n=norm(r); lab=v.get('label',k)
    W=sorted(ru.get(n,[])); E=sorted(en.get(lab.lower(),[])); D,qid=wdtt(r) if r else (None,None)
    Wg=[x[3] for x in W if not x[0]]; Wall=[x[3] for x in W]; Ew=[x[2] for x in E]
    Wn={norm(x) for x in Wall}; En={norm(x) for x in Ew}
    tt='';st='check';src=[]
    X=json.load(open('extra.json')) if 'X' not in globals() else globals()['X']; globals()['X']=X
    T=[norm(x) for x in X['ruwikt_translations'].get(r,[]) if CYR.match(norm(x))]
    P=None; pl=X['ruwiki_langlinks'].get(n) or X['ruwiki_langlinks'].get(r.lower())
    if pl:
        pv=norm(re.sub(r'\s*\(.*?\)','',pl['tt']))
        if CYR.match(pv) and len(pv.split())<=3:
            if pl['ruwiki'].lower()!=n and re.search(r'(лар|ләр|нар|нәр|тар|тәр)$',pv): pv=re.sub(r'(лар|ләр|нар|нәр|тар|тәр)$','',pv)+'*'
            P=pv
    REJ={r['ru'] for r in csv.DictReader(open('reject_tt.csv'))}
    if k in SCENE or lab.lower() in SCENE or n in REJ: pass
    elif k in OV:
        tt=OV[k]['tt'];st=OV[k]['status'];src=[OV[k]['source']]
    else:
        cand=collections.OrderedDict()
        def add(w,sname):
            w=norm(w)
            if w: cand.setdefault(w,set()).add(sname)
        for x in Wg: add(x,'rwk')
        for x in T: add(x,'rwkT')
        if D: add(D,'wd')
        if P: add(P.rstrip('*'),'wp*' if P.endswith('*') else 'wp')
        TW=json.load(open('ttwiki.json')).get(n)
        if TW and TW.get('ru') and norm(TW['ru']).startswith(n) and CYR.match(norm(re.sub(r'\s*\(.*?\)','',TW['tttitle']))): add(re.sub(r'\s*\(.*?\)','',TW['tttitle']),'ttw')
        for x in Ew: add(x,'ewk')
        for x in Wall: add(x,'rwk_lab')
        def score(it):
            w,ss=it; ind=len({x.rstrip('*').replace('_lab','').replace('T','') for x in ss})
            return (-ind, 0 if 'rwk' in ss else 1)
        if cand:
            w,ss=sorted(cand.items(),key=score)[0]
            ind={x.rstrip('*').replace('_lab','').replace('T','') for x in ss}
            tt=w
            if len(ind)>=2 and 'rwk_lab' not in ss or len(ss)>=3: st='ok'
            elif ss=={'rwk'} and W and W[0][3]==w and W[0][1]==0 and W[0][2]==0 and len(set(Wg))==1: st='ok'
            names={'rwk':'Викисловарь (тат. статья)','rwk_lab':'Викисловарь (тат. статья, с пометой)','rwkT':'Викисловарь (перевод рус. статьи)','wd':'Викиданные '+str(qid),'wp':'Википедия (интервики)','wp*':'Википедия (интервики, мн.ч. → ед.ч.)','ewk':'Wiktionary (англ.)','ttw':'Татарская Википедия (статья)'}
            src=[names[x] for x in ss]
    e={'label':lab,'tt':tt,'ru':r,'ru_stress':v.get('ru_stress',r),'status':st if tt else 'none','source':' + '.join(src)}
    dd=mkdef(tt) if tt else None
    if dd: e['def']=dd
    out[k]=e
    rows.append([k,lab,r,tt,e['status'],e['source'],'|'.join(dict.fromkeys(Wall)),'|'.join(dict.fromkeys(Ew)),D or '','|'.join(T),P or ''])
json.dump({'lang':'tt','name':'Татар','entries':out},open(ROOT+'app/src/main/assets/dictionary.json','w'),ensure_ascii=False,indent=1)
with open('tt_check.csv','w',encoding='utf-8') as f:
    c=csv.writer(f); c.writerow(['key','label','ru','tt','status','source','cand_ruwikt','cand_enwikt','cand_wikidata','cand_ruwikt_transl','cand_ruwiki_link']); c.writerows(rows)
C=collections.Counter(r[4] for r in rows); print(C, len(rows))

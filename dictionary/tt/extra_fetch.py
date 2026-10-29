import json,urllib.request,urllib.parse,time,re
d=json.load(open('mhr_dictionary.orig.json'))['entries']
words=sorted({v['ru'] for v in d.values() if v.get('ru')})
UA={'User-Agent':'PirogovLabDict/1.0 (https://pirogovlab.ru)'}
def get(host,p):
    return json.load(urllib.request.urlopen(urllib.request.Request('https://%s/w/api.php?'%host+urllib.parse.urlencode(p),headers=UA),timeout=60))
wp={};wt={}
for i in range(0,len(words),50):
    b=[w[:1].upper()+w[1:] for w in words[i:i+50]]
    j=get('ru.wikipedia.org',{'action':'query','titles':'|'.join(b),'redirects':1,'prop':'langlinks','lllang':'tt','lllimit':500,'format':'json'})
    q=j['query']; m={}
    for r in q.get('normalized',[]): m[r['to']]=r['from']
    red={r['to']:r['from'] for r in q.get('redirects',[])}
    for p in q['pages'].values():
        ll=p.get('langlinks')
        if ll:
            t=p['title']; src=red.get(t,t); src=m.get(src,src)
            wp[src.lower()]={'tt':ll[0]['*'],'ruwiki':t}
    time.sleep(0.3)
    j=get('ru.wiktionary.org',{'action':'query','titles':'|'.join(words[i:i+50]),'prop':'revisions','rvprop':'content','rvslots':'main','format':'json'})
    for p in j['query']['pages'].values():
        if 'revisions' not in p: continue
        txt=p['revisions'][0]['slots']['main']['*']
        # только первый русский раздел
        sec=txt.split('= {{-ru-}} =')[1] if '{{-ru-}}' in txt else txt
        sec=re.split(r'\n= \{\{-\w',sec)[0]
        tt=[]
        for m2 in re.finditer(r'\|tt=([^\n]*)',sec):
            v=re.sub(r'<!--.*?-->','',m2.group(1))
            v=re.sub(r'\[\[([^|\]]*\|)?([^\]]*)\]\]',r'\2',v)
            v=re.sub(r'\{\{[^}]*\}\}','',v)
            v=re.split(r'\|',v)[0]
            for pp in re.split(r"[,;]",v):
                pp=pp.strip(" '\t")
                if pp: tt.append(pp)
        if tt: wt[p['title']]=tt
    time.sleep(0.3)
json.dump({'ruwiki_langlinks':wp,'ruwikt_translations':wt},open('extra.json','w'),ensure_ascii=False,indent=0)
print(len(words),len(wp),len(wt))

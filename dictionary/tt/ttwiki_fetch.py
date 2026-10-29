import json,urllib.request,urllib.parse,time,csv
R=list(csv.DictReader(open('tt_check.csv')))
miss=sorted({r['ru'] for r in R if not r['tt'] and r['ru']})
UA={'User-Agent':'PirogovLabDict/1.0 (https://pirogovlab.ru)'}
def get(host,p): return json.load(urllib.request.urlopen(urllib.request.Request('https://%s/w/api.php?'%host+urllib.parse.urlencode(p),headers=UA),timeout=60))
out={}
for i in range(0,len(miss),50):
    b=[w[:1].upper()+w[1:] for w in miss[i:i+50]]
    j=get('tt.wikipedia.org',{'action':'query','titles':'|'.join(b),'redirects':1,'prop':'langlinks|pageprops','lllang':'ru','format':'json'})
    q=j['query']; red={r['to']:r['from'] for r in q.get('redirects',[])}; nm={r['to']:r['from'] for r in q.get('normalized',[])}
    for p in q['pages'].values():
        if 'missing' in p or 'disambiguation' in p.get('pageprops',{}): continue
        t=p['title']; s=red.get(t,t); s=nm.get(s,s)
        out[s.lower()]={'tttitle':t,'ru':(p.get('langlinks') or [{}])[0].get('*'),'qid':p.get('pageprops',{}).get('wikibase_item')}
    time.sleep(0.3)
json.dump(out,open('ttwiki.json','w'),ensure_ascii=False,indent=0)
print(len(miss),len(out))
for k,v in out.items(): print(k,v)

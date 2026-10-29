import json,urllib.request,urllib.parse,time
d=json.load(open('../../app/src/main/assets/dictionary.json'))['entries']
titles=sorted({v['ru'][:1].upper()+v['ru'][1:] for v in d.values() if v.get('ru')})
out={}
for i in range(0,len(titles),50):
    b=titles[i:i+50]
    q=urllib.parse.urlencode({'action':'wbgetentities','sites':'ruwiki','titles':'|'.join(b),'props':'labels|sitelinks|descriptions','languages':'tt|ru','sitefilter':'ttwiki|ruwiki','format':'json'})
    r=urllib.request.Request('https://www.wikidata.org/w/api.php?'+q,headers={'User-Agent':'PirogovLab-dict/1.0 (pirogovlab.ru)'})
    j=json.load(urllib.request.urlopen(r,timeout=60))
    if 'error' in j: print(j['error'])
    for qid,e in j.get('entities',{}).items():
        if 'missing' in e: continue
        rt=e.get('sitelinks',{}).get('ruwiki',{}).get('title')
        out[rt]={'qid':qid,'tt':e.get('labels',{}).get('tt',{}).get('value'),'ttwiki':e.get('sitelinks',{}).get('ttwiki',{}).get('title'),'rudesc':e.get('descriptions',{}).get('ru',{}).get('value')}
    time.sleep(0.5)
json.dump(out,open('wikidata.json','w'),ensure_ascii=False,indent=0)
print(len(titles),len(out),sum(1 for v in out.values() if v['tt']))

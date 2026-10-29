# ImageNet-1k (EfficientNet-Lite0) → словарь. Алиасы на существующие метки или новые метки in:<класс> с русским словом;
# марийское слово ищется тем же способом, что и для ML Kit (Учаев/fulab, без помет). Нет слова — mhr пустой («нет перевода»).
INL=[l.strip() for l in open('/tmp/in.txt')] if False else None
import zipfile
INL=zipfile.ZipFile('../app/src/main/assets/imagenet.tflite.z').read('labels_without_background.txt').decode().split('\n')
INL=[l.strip() for l in INL if l.strip()]
alias={}
for i in range(151,269): alias[INL[i]]='dog'
for i in range(281,286): alias[INL[i]]='cat'
for line in open('imagenet_map.txt',encoding='utf8'):
    if not line.strip() or line.startswith('#'): continue
    lab,t=line.rstrip('\n').split('|')
    if lab not in INL: print('skip',lab); continue
    if t.startswith('='):
        k=t[1:].lower(); assert k in out,k; alias[lab]=k; continue
    key='in:'+lab.lower(); alias[lab]=key
    if key in out: continue
    ru=nfc(t); n=norm(ru); mhr='';src='';status='check';cand=[]
    if n in fwi: mhr=fwi[n][0];src='mari-filword';status='ok';cand=fwi[n]
    elif n in ci:
        hits=sorted(ci[n],key=lambda h:(norm(h[0])==n,h[1],h[2]))
        cand=list(dict.fromkeys(h[0] for h in hits)); mhr=cand[0];src='Словарь марийского языка 10 т. ('+hits[0][3]+')'
        status='ok' if hits[0][1]==1 and hits[0][2]==0 and len(cand)==1 else 'check'
    if mhr and hill(mhr): status='check'
    if key in ov: o=ov[key]; mhr=o['mhr']; status=o['status'] or 'ok'; src='override: '+o['source']; cand=[mhr]
    out[key]={'label':lab,'mhr':mhr,'ru':re.sub('\u0301','',ru),'ru_stress':ru,'status':status,'source':src,'model':'imagenet'}
    rows.append([key,mhr,ru,status,src,' / '.join(cand[:5])])
json.dump(alias,open('../app/src/main/assets/imagenet_map.json','w'),ensure_ascii=False,indent=0)
print('imagenet aliases',len(alias),'new',sum(1 for k in out if k.startswith('in:')))

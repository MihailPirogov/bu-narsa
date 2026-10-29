# Иконка «Бу нәрсә?»: изумрудный фон с золотой ромбической сеткой, белая камера, тюльпан в объективе
import re,cairosvg,os
G='#0E5E3B';GD='#0B4A2F';Y='#C8962E';R='#B3123A';W='#FFFFFF'
fg0=open('app/src/main/res/drawable/ic_launcher_foreground.xml').read()
cam=re.search(r'<path android:fillColor="#FFFFFFFF" android:pathData="([^"]+)"/>',fg0).group(1)
T=["......r......",".....rrr.....","..r..rrr..r..","..rr.ryr.rr..","..rrrryrrrr..","..rrryyyrrr..","...rrryrrr...","....rrrrr....","......g......",".gg...g...gg.","gggg..g..gggg",".gggg.g.gggg.","...ggggggg..."]
cell=2.3; x0=54-13*cell/2; y0=57.5-13*cell/2
def px(ch):
    return " ".join(f"M{x0+i*cell:.2f},{y0+j*cell:.2f}h{cell+0.05:.2f}v{cell+0.05:.2f}h-{cell+0.05:.2f}z" for j,r in enumerate(T) for i,c in enumerate(r) if c==ch)
def dia(cx,cy,r): return f"M{cx},{cy-r} L{cx+r},{cy} L{cx},{cy+r} L{cx-r},{cy} Z"
bg=[(G,"M0,0h108v108h-108z"),(GD," ".join(dia(x,y,6) for x in range(0,109,18) for y in range(0,109,18)))]
fg=[(W,cam),(Y,"M54,40.5a17,17 0 1 1 0,34a17,17 0 1 1 0,-34z"),(W,"M54,42.5a15,15 0 1 1 0,30a15,15 0 1 1 0,-30z"),(R,px('r')),(Y,px('y')),(G,px('g')),(Y,"M72.14,40.97H78.89V44.83H72.14Z")]
def vec(items,mono=False):
    o=['<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">']
    for c,d in items: o.append(f'<path android:fillColor="{"#FFFFFFFF" if mono else c}" android:pathData="{d}"/>')
    return "\n".join(o+['</vector>'])
D='app/src/main/res/'
open(D+'drawable/ic_launcher_background.xml','w').write(vec(bg))
open(D+'drawable/ic_launcher_foreground.xml','w').write(vec(fg))
open(D+'drawable/ic_launcher_monochrome.xml','w').write(vec([(W,cam)]))
def svg(items,clip):
    p="".join(f'<path fill="{c}" d="{d}"/>' for c,d in items)
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><clipPath id="c"><rect x="0" y="0" width="108" height="108" rx="{clip}"/></clipPath><g clip-path="url(#c)"><g transform="translate(54 54) scale(1.45) translate(-54 -54)">{p.replace("<path","<path")}</g></g></svg>'
s=svg(bg+fg,24).replace('<g transform="translate(54 54) scale(1.45) translate(-54 -54)"><path fill="#0E5E3B" d="M0,0h108v108h-108z"/>','<path fill="#0E5E3B" d="M0,0h108v108h-108z"/><g transform="translate(54 54) scale(1.45) translate(-54 -54)">')
for dn,sz in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    cairosvg.svg2png(bytestring=s.encode(),write_to=D+f'mipmap-{dn}/ic_launcher.png',output_width=sz,output_height=sz)
cairosvg.svg2png(bytestring=s.encode(),write_to='design/icon_tt_512.png',output_width=512,output_height=512)

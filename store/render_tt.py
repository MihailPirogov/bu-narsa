from PIL import Image, ImageDraw, ImageFont
T='/workspace/tatar-kupu/'
F=T+'app/src/main/res/font/golos_%d.ttf'
def g(w,s): return ImageFont.truetype(F%w,int(s))
G=(14,94,59);GD=(11,74,47);Y=(200,150,46);R=(179,18,58);W=(255,255,255);BG=(251,248,241)
TUL=["......r......",".....rrr.....","..r..rrr..r..","..rr.ryr.rr..","..rrrryrrrr..","..rrryyyrrr..","...rrryrrr...","....rrrrr....","......g......",".gg...g...gg.","gggg..g..gggg",".gggg.g.gggg.","...ggggggg..."]
def tulip(d,x,y,c,leaf=G):
    for j,r in enumerate(TUL):
        for i,ch in enumerate(r):
            col={'r':R,'y':Y,'g':leaf}.get(ch)
            if col: d.rectangle([x+i*c,y+j*c,x+(i+1)*c-1,y+(j+1)*c-1],fill=col)
def band(d,w,y,c,bg=BG,leaf=G):
    d.rectangle([0,y,w,y+c*16],fill=bg)
    x=0
    while x<w: tulip(d,x,y+c*1.5,c,leaf); x+=c*18
def lattice(im,col):
    d=ImageDraw.Draw(im); w,h=im.size; s=int(h/6)
    for x in range(0,w+s,s):
        for y in range(0,h+s,s): d.polygon([(x,y-s/3),(x+s/3,y),(x,y+s/3),(x-s/3,y)],fill=col)
icon=Image.open(T+'design/icon_tt_512.png').convert('RGBA')
icon.save('play/play_icon_512.png')
def promo(w,h,title,sub,shot_frac,safe_right=0.2,shot=None):
    im=Image.new('RGB',(w,h),G); lattice(im,GD); d=ImageDraw.Draw(im)
    c=max(3,h//90); band(d,w,h-c*16,c)
    ic=icon.resize((int(h*0.3),)*2); im.paste(ic,(int(w*0.05),int(h*0.1)),ic)
    tx=int(w*0.05); ty=int(h*0.1+h*0.32)
    d.text((tx,ty),title,font=g(700,h*0.15),fill=W)
    for k,line in enumerate(sub.split('\n')): d.text((tx,ty+h*0.19+k*h*0.075),line,font=g(500,h*0.06),fill=(240,225,190))
    if shot:
        s=Image.open(shot).convert('RGB'); sh=int(h*shot_frac); s=s.resize((int(s.width*sh/s.height),sh))
        x=int(w*(1-safe_right))-s.width-int(w*0.015); y=int(h*0.07)
        m=Image.new('L',s.size,0); ImageDraw.Draw(m).rounded_rectangle([0,0,s.width-1,s.height-1],int(s.width*0.07),fill=255)
        d.rounded_rectangle([x-6,y-6,x+s.width+6,y+s.height+6],int(s.width*0.08),fill=W)
        im.paste(s,(x,y),m)
    return im
SH=T+'tests/screens/1080x1920_420dpi__3_result_pets.png'
promo(1024,500,'Бу нәрсә?','Камера-словарь\nтатарского языка',0.78,0.2,SH).save('play/feature_graphic_1024x500.png')
promo(1200,630,'Бу нәрсә?','Камера-словарь\nтатарского языка',0.78,0.06,SH).save('site/blog-1200x630.jpg',quality=88)
promo(1200,630,'Бу нәрсә?','Камера-словарь\nтатарского языка',0.78,0.06,SH).resize((960,540)).save('site/blog-thumb-960x540.webp',quality=85)
promo(480,320,'Бу нәрсә?','Татарская\nкамера-словарь',0.8,0.03,SH).save('site/kartochka-480x320.webp',quality=88)
for i,n in enumerate(['3_result_pets','4_result_desk','5_result_street','1_home','6_result_long'],1):
    s=Image.open(T+f'tests/screens/1080x1920_420dpi__{n}.png').convert('RGB'); print(n,s.size)
    s.resize((1080,1920)).save(f'play/phone_{i}_{n.split("_",1)[1]}.png')

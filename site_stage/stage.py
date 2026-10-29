import re,os,json,shutil
from PIL import Image
L='live/';O='out/';S='/workspace/tatar-kupu/store/'
def rd(p): return open(L+p,encoding='utf-8').read()
def wr(p,s): os.makedirs(os.path.dirname(O+p),exist_ok=True); open(O+p,'w',encoding='utf-8').write(s)
os.makedirs(O+'blog/media',exist_ok=True)
shutil.copy(S+'site/kartochka-480x320.webp',O+'bu-narsa/img/kartochka-bu-narsa.webp')
shutil.copy(S+'site/blog-1200x630.jpg',O+'bu-narsa/img/og.jpg')
shutil.copy(S+'site/blog-1200x630.jpg',O+'blog/media/bu-narsa.jpg')
shutil.copy(S+'site/blog-thumb-960x540.webp',O+'blog/media/bu-narsa-thumb.webp')
Image.open(S+'play/feature_graphic_1024x500.png').convert('RGB').save(O+'bu-narsa/img/feature-graphic.webp',quality=85)
TITLE='Бу нәрсә? — татарский словарь по камере: узнай слово по-татарски | Pirogov Lab'
D='Бесплатное приложение для Android: наведите камеру на предмет — и узнайте, как он называется по-татарски (татарча). Татарский словарь по фото, перевод и толкование. Помогает выучить татарский язык. Работает без интернета.'
KW='татарский словарь, татарский по камере, переводчик татарский по фото, выучить татарский, учить татарский язык, татарча, по-татарски, татар теле, татарский для детей, Бу нәрсә'
p=open(O+'bu-narsa/index.html',encoding='utf-8').read()
p=re.sub(r'<title>.*?</title>',f'<title>{TITLE}</title>',p,1)
p=re.sub(r'<meta name="description" content="[^"]*">',f'<meta name="description" content="{D}">\n<meta name="keywords" content="{KW}">',p,1)
p=re.sub(r'<meta property="og:description" content="[^"]*">',f'<meta property="og:description" content="{D}">',p,1)
p=re.sub(r'<meta property="og:title" content="[^"]*">','<meta property="og:title" content="Бу нәрсә? — татарский словарь по камере">',p,1)
p=p.replace('<meta property="og:locale" content="ru_RU">','<meta property="og:locale" content="ru_RU"><meta property="og:url" content="https://pirogovlab.ru/bu-narsa/">',1)
if 'hreflang="ru"' not in p: p=p.replace('<link rel="canonical" href="https://pirogovlab.ru/bu-narsa/">','<link rel="canonical" href="https://pirogovlab.ru/bu-narsa/">\n<link rel="alternate" hreflang="ru" href="https://pirogovlab.ru/bu-narsa/">\n<link rel="alternate" hreflang="tt" href="https://pirogovlab.ru/bu-narsa/">\n<link rel="alternate" hreflang="x-default" href="https://pirogovlab.ru/bu-narsa/">',1)
ld={"@context":"https://schema.org","@type":"SoftwareApplication","name":"Бу нәрсә?","alternateName":["Бу нарса","Bu Närsä","Татарский словарь по камере"],"description":D,"applicationCategory":"EducationalApplication","operatingSystem":"Android 7.0+","softwareVersion":"0.1.0","datePublished":"2026-10-29","inLanguage":["tt","ru"],"url":"https://pirogovlab.ru/bu-narsa/","downloadUrl":"https://pirogovlab.ru/dl/bu-narsa-0.1.0.apk","fileSize":"90MB","image":"https://pirogovlab.ru/bu-narsa/img/og.jpg","screenshot":["https://pirogovlab.ru/bu-narsa/img/screen-pets.webp","https://pirogovlab.ru/bu-narsa/img/screen-street.webp"],"offers":{"@type":"Offer","price":"0","priceCurrency":"RUB"},"author":{"@type":"Person","name":"Михаил Пирогов","url":"https://pirogovlab.ru/about/"},"publisher":{"@type":"Organization","name":"Pirogov Lab","url":"https://pirogovlab.ru/"},"isAccessibleForFree":True,"sameAs":["https://github.com/MihailPirogov/bu-narsa"]}
bc={"@context":"https://schema.org","@type":"BreadcrumbList","itemListElement":[{"@type":"ListItem","position":1,"name":"Pirogov Lab","item":"https://pirogovlab.ru/"},{"@type":"ListItem","position":2,"name":"Бу нәрсә?","item":"https://pirogovlab.ru/bu-narsa/"}]}
p=re.sub(r'<script type="application/ld\+json">.*?</script>\n?','',p,flags=re.S)
p=p.replace('</head>','<script type="application/ld+json">'+json.dumps(ld,ensure_ascii=False)+'</script>\n<script type="application/ld+json">'+json.dumps(bc,ensure_ascii=False)+'</script>\n</head>',1)
p=p.replace('<h1>Бу нәрсә? — камера-словарь татарского языка</h1>','<h1>Бу нәрсә? — татарский словарь по камере</h1>')
p=p.replace('<p class="eyebrow"><span>Татар теле · Android</span></p>','<p class="eyebrow"><span>Татар теле · татарча · Android</span></p>')
p=p.replace('</section>\n  <aside class="callout"','</section>\n  <section aria-labelledby="faq"><h2 id="faq">Вопросы</h2><div class="card"><p><b>Как выучить татарские слова с помощью камеры?</b> Наведите камеру на предмет — «Бу нәрсә?» покажет, как он называется по-татарски, и русский перевод. Так удобно запоминать слова дома, на улице и вместе с детьми.</p><p><b>Нужен ли интернет?</b> Нет, распознавание и словарь работают на телефоне.</p><p><b>Есть игра на татарском?</b> Да — <a href="/suz-donyasy-igra/">«Сүз дөньясы»</a>, словесная головоломка с переводом каждого слова.</p><p><b>Исходный код</b> — <a href="https://github.com/MihailPirogov/bu-narsa" rel="noopener">на GitHub</a>.</p></div></section>\n  <aside class="callout"',1)
assert 'id="faq"' in p
wr('bu-narsa/index.html',p)
# ---- blog post
t=rd('blog/2026-10-24-mo-tide/index.html')
BT='«Бу нәрсә?»: камера, которая говорит по-татарски'; BD='Наведите камеру на предмет — и узнайте, как он называется по-татарски: слово, перевод и толкование. Татарский словарь по фото, работает без интернета.'
U='https://pirogovlab.ru/blog/2026-10-29-bu-narsa/'
t=re.sub(r'<title>.*?</title>',f'<title>{BT} | Pirogov Lab</title>',t,1)
t=re.sub(r'<meta name="description" content="[^"]*">',f'<meta name="description" content="{BD}">',t,1)
t=re.sub(r'<link rel="alternate" hreflang="(en|ru|x-default)"[^>]*>\n?','',t)
t=t.replace('https://pirogovlab.ru/blog/2026-10-24-mo-tide/',U)
t=re.sub(r'<meta property="og:title" content="[^"]*">',f'<meta property="og:title" content="{BT}">',t)
t=re.sub(r'<meta property="og:description" content="[^"]*">',f'<meta property="og:description" content="{BD}">',t)
t=re.sub(r'<meta name="twitter:(title|description)" content="[^"]*">','',t)
t=t.replace('https://pirogovlab.ru/blog/media/mo-tide.jpg','https://pirogovlab.ru/blog/media/bu-narsa.jpg')
t=re.sub(r'<script type="application/ld\+json">.*?</script>\n?','',t,flags=re.S)
art={"@context":"https://schema.org","@type":"BlogPosting","headline":BT,"description":BD,"datePublished":"2026-10-29T15:30:00+03:00","image":"https://pirogovlab.ru/blog/media/bu-narsa.jpg","url":U,"inLanguage":"ru","author":{"@type":"Person","name":"Михаил Пирогов"},"publisher":{"@type":"Organization","name":"Pirogov Lab"}}
t=t.replace('</head>','<script type="application/ld+json">'+json.dumps(art,ensure_ascii=False)+'</script>\n</head>',1)
nav_crumb_fix=True
a=t.index('<main'); b=t.index('</main>')+7
main=f'''<main class="wrap">
<article class="full">
  <p class="eyebrow"><a href="/blog/">Микроблог</a></p>
  <h1>{BT}</h1>
  <time class="date" datetime="2026-10-29T15:30:00+03:00">29 октября 2026</time>
  <figure class="media"><img src="/blog/media/bu-narsa.jpg" width="1200" height="630" alt="{BT}"></figure>
  <div class="body">
<p>Вслед за марийской «Мо тиде?» сделал татарскую версию — <b>«Бу нәрсә?»</b> («Что это?»), камера-словарь татарского языка. Наводите камеру на предмет или выбираете фото из галереи — на объектах появляются номера, а под фото — татарские слова и русский перевод.</p>
<figure class="media shots"><img src="/bu-narsa/img/screen-pets.webp" width="540" height="1200" alt="Кошка и собака: мәче, эт" loading="lazy" style="aspect-ratio:540/1200"><img src="/bu-narsa/img/screen-street.webp" width="540" height="1200" alt="Улица" loading="lazy" style="aspect-ratio:540/1200"><img src="/bu-narsa/img/screen-desk.webp" width="540" height="1200" alt="Рабочий стол" loading="lazy" style="aspect-ratio:540/1200"></figure>
<h2>Что умеет</h2>
<ul>
<li>Узнаёт <b>сотни предметов</b>: людей, животных и птиц, посуду, мебель, одежду, транспорт, фрукты, деревья, небо и воду. До 8 предметов на одном фото.</li>
<li>Показывает <b>татарское слово, русский перевод с ударением и толкование</b>; буквы ә ө ү җ ң һ — крупно.</li>
<li><b>Живой режим</b> — подписи прямо в видоискателе.</li>
<li>Картинку с подписями можно <b>сохранить</b> и <b>отправить</b> друзьям.</li>
<li>Работает <b>без интернета</b>: фотографии никуда не отправляются.</li>
</ul>
<p>Слова собраны из Викисловаря и Википедии; слова, найденные только в одном источнике, помечены значком ⚠ и ждут проверки носителем языка. Оформление — татарский тюльпан и изумрудно-золотая палитра. Исходный код — <a href="https://github.com/MihailPirogov/bu-narsa" rel="noopener">на GitHub</a>.</p>
<p>Нашли ошибку в слове или переводе? Напишите мне в <a href="/contacts/">Telegram</a> — буду исправлять. Рәхмәт!</p>
  </div>
  <div class="stores"><a class="btn" href="/dl/bu-narsa-0.1.0.apk" download>⬇ Скачать APK</a></div>
  <div class="meta"><span class="pill">Версия 0.1.0</span><span class="pill">Android 7.0+</span><a class="pill" href="/bu-narsa/">Страница приложения</a><span class="pill">Бесплатно</span></div>
  <a class="proj" href="/bu-narsa/"><img src="/bu-narsa/img/kartochka-bu-narsa.webp" width="480" height="320" alt=""><div><b>Бу нәрсә?</b><span>Страница приложения: возможности, скриншоты, скачать</span></div><span class="go" aria-hidden="true">→</span></a>
  <a class="backlink" href="/blog/">← Все записи</a>
</article>
</main>'''
t=t[:a]+main+t[b:]
t=re.sub(r'<div class="lang".*?</div>','',t,flags=re.S)
pass
wr('blog/2026-10-29-bu-narsa/index.html',t)
import subprocess
# ---- home
h=rd('index.html')
mb='<a class="mbf" href="/blog/2026-10-29-bu-narsa/"><img src="/blog/media/bu-narsa-thumb.webp" width="960" height="540" alt=""><div><time datetime="2026-10-29">29 октября 2026</time><b>«Бу нәрсә?»: камера, которая говорит по-татарски</b><span>Наведите камеру на предмет — и узнайте, как он называется по-татарски. Работает без интернета.</span></div></a>\n      '
k='<h2 id="mb-h">Микроблог</h2>\n      '; assert h.count(k)==1; h=h.replace(k,k+mb)
pj='  <a class="proj" href="/bu-narsa/"><img src="/bu-narsa/img/kartochka-bu-narsa.webp" width="480" height="320" alt="Бу нәрсә? — татарский словарь по камере"><div><b>Бу нәрсә? — татарская камера-словарь</b><span>Наведите камеру — узнайте, как предмет называется по-татарски. APK, без интернета</span></div><span class="go" aria-hidden="true">→</span></a>\n'
k='<h2 id="pj-h">Проекты</h2><!-- blog:home:end -->\n'; assert h.count(k)==1; h=h.replace(k,k+pj)
wr('index.html',h)
# ---- blog index
bi=rd('blog/index.html'); k='  <article class="card post">'; i=bi.index(k)
bi=bi[:i]+f'''  <article class="card post">
    <time class="date" datetime="2026-10-29T15:30:00+03:00">29 октября 2026</time>
    <h2><a href="/blog/2026-10-29-bu-narsa/">{BT}</a></h2>
    <figure class="media"><img src="/blog/media/bu-narsa.jpg" width="1200" height="630" alt="Бу нәрсә? — камера-словарь татарского языка" loading="lazy"></figure>
    <p>{BD} <a href="/bu-narsa/">Страница приложения</a> #татарский #татарча #Android</p>
    <a class="more" href="/blog/2026-10-29-bu-narsa/">Читать полностью →</a>
  </article>
'''+bi[i:]
wr('blog/index.html',bi)
r=rd('blog/rss.xml'); i=r.index('    <item>')
r=r[:i]+f'''    <item>
      <title>{BT}</title>
      <link>{U}</link>
      <guid isPermaLink="true">{U}</guid>
      <pubDate>Thu, 29 Oct 2026 15:30:00 +0300</pubDate>
      <description>{BD}</description>
    </item>
'''+r[i:]
r=re.sub(r'<lastBuildDate>.*?</lastBuildDate>','<lastBuildDate>Thu, 29 Oct 2026 15:30:00 +0300</lastBuildDate>',r,1)
wr('blog/rss.xml',r)
sm=rd('sitemap.xml'); assert 'bu-narsa' not in sm
sm=sm.replace('</urlset>','  <url><loc>https://pirogovlab.ru/bu-narsa/</loc><lastmod>2026-10-29</lastmod></url>\n  <url><loc>https://pirogovlab.ru/blog/2026-10-29-bu-narsa/</loc><lastmod>2026-10-29</lastmod></url>\n</urlset>')
sm=re.sub(r'(<loc>https://pirogovlab.ru/</loc><lastmod>)[^<]*',r'\g<1>2026-10-29',sm)
sm=re.sub(r'(<loc>https://pirogovlab.ru/blog/</loc><lastmod>)[^<]*',r'\g<1>2026-10-29',sm)
wr('sitemap.xml',sm)
print('ok')

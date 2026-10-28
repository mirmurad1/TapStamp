// Trend Solutions saytı: dil seçimi (az/en/ru), demo kart, scroll animasiyaları və demo müraciəti forması.
const T={
az:{
 'form.sending':'Göndərilir…','form.errLimit':'Çox sayda müraciət göndərildi. Bir saatdan sonra yenidən cəhd edin.','form.errInvalid':'Sahələri yoxlayın: telefon nömrəsində yalnız rəqəm, boşluq və + ola bilər.','form.errGeneric':'Müraciət göndərilmədi. Bir azdan yenidən cəhd edin və ya <a href="mailto:info@trendsolutions.az">info@trendsolutions.az</a> ünvanına yazın.',
 'nav.label':'Əsas menyu','nav.lang':'Dil','nav.services':'Xidmətlər','nav.demo':'Demo','nav.contact':'Əlaqə',
 'hero.new':'Yeni','hero.pill':'TapStamp: NFC ilə rəqəmsal möhür kartı',
 'hero.title':'Biznesiniz üçün <span class="grad">etibarlı proqram təminatı</span> hazırlayırıq',
 'hero.lead':'Biz Azərbaycanda fəaliyyət göstərən proqram təminatı şirkətiyik. Sifarişlə veb və mobil tətbiqlər hazırlayır, eyni zamanda öz məhsullarımızı yaradırıq.',
 'hero.cta':'Layihənizi müzakirə edək','hero.cta2':'Xidmətlərimiz',
 'w.custom':'Fərdi proqram təminatı','w.customSub':'Veb, mobil və inteqrasiyalar','w.tapSub':'NFC əsaslı sadiqlik proqramı','w.next':'Yeni məhsullar','w.nextSub':'Tezliklə',
 'dirs.kicker':'Fəaliyyətimiz','dirs.title':'İki istiqamətdə işləyirik',
 'dirs.customText':'Hazır həllər ehtiyacınızı ödəmirsə, işinizə tam uyğun sistemi birlikdə qururuq: veb və mobil tətbiqlər, idarəetmə panelləri, inteqrasiyalar.','dirs.more':'Ətraflı',
 'dirs.tapTitle':'Məhsulumuz: TapStamp','dirs.tapText':'Kafe, gözəllik salonu və mağazalar üçün rəqəmsal möhür kartı. Müştəri telefonunu NFC çipə yaxınlaşdırır, möhür toplayır və hədiyyə qazanır.','dirs.tapMore':'Məhsulla tanış olun',
 'custom.title':'Hazır həll uyğun gəlmirsə, sizin üçün hazırlayırıq',
 'custom.sub':'Əvvəlcə ekranların maketini hazırlayırıq. Siz təsdiq etdikdən sonra proqramlaşdırmaya keçirik və hər mərhələnin nəticəsini sizə göstəririk.',
 'custom.webT':'Veb tətbiqlər və idarəetmə panelləri','custom.webP':'Sifariş, anbar və müştəri idarəetməsi (CRM) sistemləri.',
 'custom.mobT':'Mobil tətbiqlər','custom.mobP':'iPhone və Android üçün tətbiqlər, eləcə də telefona quraşdırılan veb tətbiqlər.',
 'custom.intT':'İnteqrasiyalar','custom.intP':'Ödəniş sistemləri, SMS xidmətləri, kassa proqramları və mövcud sistemlərinizlə əlaqə.',
 'custom.supT':'Dəstək və təhlükəsizlik','custom.supP':'İstifadəyə verildikdən sonra server, yeniləmələr və ehtiyat nüsxələr bizim nəzarətimizdədir.',
 'tap.kicker':'Məhsulumuz','tap.title':'Müştəriləriniz <span class="grad">bir toxunuşla geri qayıtsın</span>',
 'tap.lead':'Kağız möhür kartının yerini NFC çip və telefon tutur. Müştərinin heç bir tətbiq yükləməsinə ehtiyac yoxdur.',
 'tap.cta':'Pulsuz demo istəyin','tap.cta2':'İmkanlar',
 'card.company':'Qəhvə Evi','card.title':'Möhür kartınız','card.rule':'Hər 6-cı qəhvə hədiyyədir',
 'card.tap':'Telefonu çipə yaxınlaşdırın','card.wait':'İşçi təsdiqi gözlənilir…','sticky.demo':'Demo al','card.added':'Möhür əlavə olundu','card.full':'Hədiyyəniz hazırdır! Yenidən başlayın',
 'card.caption':'Düyməyə basın və möhürün necə əlavə olunduğuna baxın.',
 'feat.kicker':'TapStamp imkanları','feat.title':'Kassada kart axtarmağa ehtiyac qalmır',
 'feat.sub':'Kafe, gözəllik salonu, avtoyuma, mağaza: müştəriləriniz təkrar gəlirsə, TapStamp sizin üçündür.',
 'feat.tapT':'Bir toxunuşla möhür','feat.tapP':'Müştəri telefonunu kassanın yanındakı çipə yaxınlaşdırır və möhür avtomatik olaraq kartına əlavə olunur. Çip hər toxunuşda yeni kod yaradır, buna görə möhürü saxtalaşdırmaq mümkün deyil.',
 'feat.s1':'Toxunur','feat.s1p':'Telefonda şirkətinizin səhifəsi açılır',
 'feat.s2':'Möhür toplayır','feat.s2p':'İlk dəfə nömrə ilə qeydiyyatdan keçir, sonrakı hər toxunuş bir möhürdür',
 'feat.s3':'Hədiyyəni alır','feat.s3p':'İşçi planşetdə QR kodu oxudaraq hədiyyəni təsdiqləyir',
 'feat.appT':'Tətbiq yükləmədən','feat.appP':'Brauzerdə açılır, istəyən ana ekrana əlavə edə bilər.',
 'feat.langT':'Üç dildə','feat.langP':'Müştəri istədiyi dili seçir.',
 'feat.giftT':'Hədiyyələr və dəvətlər','feat.giftP':'Müştəri dostunu dəvət edir və hər ikisi bonus möhür qazanır.',
 'feat.g1':'Pulsuz qəhvə','feat.g1s':'6 möhürdən sonra','feat.g2':'+1 möhür','feat.g2s':'hər dəvət üçün',
 'feat.statT':'Müştərilərinizi tanıyın','feat.statP':'Ziyarətləri, geri qayıdan müştəriləri və verilən hədiyyələri izləyin. Uzun müddət gəlməyən müştərilərə bildiriş və ya SMS göndərin.',
 'demo.title':'TapStamp-ı pulsuz sınayın','demo.sub':'Formu doldurun. Sizinlə əlaqə saxlayıb şirkətinizə uyğun demo hazırlayacağıq.',
 'demo.p1':'Şirkətinizin adı və rəngi ilə hazır səhifə','demo.p2':'İlk NFC çip pulsuzdur','demo.p3':'Bir iş günü ərzində sizinlə əlaqə saxlayırıq',
 'form.name':'Adınız','form.phone':'Telefon nömrəniz','form.company':'Şirkətin adı','form.type':'Fəaliyyət sahəsi',
 'form.cafe':'Kafe','form.restaurant':'Restoran','form.beauty':'Gözəllik salonu','form.barber':'Bərbər','form.carwash':'Avtoyuma','form.other':'Digər',
 'form.branches':'Filial sayı','form.many':'6 və daha çox','form.note':'Əlavə qeyd','form.optional':'(istəyə görə)',
 'form.submit':'Müraciət göndər','form.consent':'Müraciət göndərməklə məlumatlarınızın yalnız sizinlə əlaqə saxlamaq üçün istifadə olunmasına razılıq verirsiniz.',
 'form.doneT':'Müraciətiniz qəbul olundu','form.doneP':'Bir iş günü ərzində sizinlə əlaqə saxlayacağıq.',
 'contact.title':'Sualınız var?','contact.sub':'Bizə yazın və ya zəng edin.','contact.email':'Email','contact.phone':'Telefon',
 'footer.copy':'© 2026 Trend Solutions. Bütün hüquqlar qorunur.'
},
en:{
 'form.sending':'Sending…','form.errLimit':'Too many requests. Please try again in an hour.','form.errInvalid':'Please check the fields: the phone number may contain only digits, spaces and +.','form.errGeneric':'Your request was not sent. Please try again shortly or write to <a href="mailto:info@trendsolutions.az">info@trendsolutions.az</a>.',
 'nav.label':'Main menu','nav.lang':'Language','nav.services':'Services','nav.demo':'Demo','nav.contact':'Contact',
 'hero.new':'New','hero.pill':'TapStamp: a digital stamp card with NFC',
 'hero.title':'We build <span class="grad">reliable software</span> for your business',
 'hero.lead':'We are a software company based in Azerbaijan. We build custom web and mobile applications and create our own products.',
 'hero.cta':'Discuss your project','hero.cta2':'Our services',
 'w.custom':'Custom software','w.customSub':'Web, mobile and integrations','w.tapSub':'NFC-based loyalty program','w.next':'New products','w.nextSub':'Coming soon',
 'dirs.kicker':'What we do','dirs.title':'Two lines of work',
 'dirs.customText':'When off-the-shelf tools don’t fit, we build a system around the way you work: web and mobile apps, admin panels and integrations.','dirs.more':'Learn more',
 'dirs.tapTitle':'Our product: TapStamp','dirs.tapText':'A digital stamp card for cafés, beauty salons and shops. Customers tap their phone on an NFC chip, collect stamps and earn rewards.','dirs.tapMore':'Explore the product',
 'custom.title':'If a ready-made solution doesn’t fit, we build yours',
 'custom.sub':'We start with mockups of every screen. Once you approve them, we move to development and show you the result of each stage.',
 'custom.webT':'Web apps and admin panels','custom.webP':'Order, inventory and customer management (CRM) systems.',
 'custom.mobT':'Mobile apps','custom.mobP':'Apps for iPhone and Android, as well as installable web apps.',
 'custom.intT':'Integrations','custom.intP':'Payment systems, SMS services, POS software and the tools you already use.',
 'custom.supT':'Support and security','custom.supP':'After launch, we take care of servers, updates and backups.',
 'tap.kicker':'Our product','tap.title':'Bring customers back <span class="grad">with a single tap</span>',
 'tap.lead':'An NFC chip and a phone replace the paper stamp card. Customers don’t need to install any app.',
 'tap.cta':'Request a free demo','tap.cta2':'Features',
 'card.company':'Coffee House','card.title':'Your stamp card','card.rule':'Every 6th coffee is free',
 'card.tap':'Hold your phone to the chip','card.wait':'Waiting for staff approval…','sticky.demo':'Get a demo','card.added':'Stamp added','card.full':'Your reward is ready! Start over',
 'card.caption':'Press the button to see how a stamp is added.',
 'feat.kicker':'TapStamp features','feat.title':'No more searching for cards at the till',
 'feat.sub':'Cafés, beauty salons, car washes, shops: if your customers come back, TapStamp is for you.',
 'feat.tapT':'One tap, one stamp','feat.tapP':'The customer holds their phone to the chip by the till and the stamp is added to their card automatically. The chip generates a new code on every tap, so stamps can’t be faked.',
 'feat.s1':'Tap','feat.s1p':'Your business page opens on the phone',
 'feat.s2':'Collect','feat.s2p':'Sign up once with a phone number; every tap after that is a stamp',
 'feat.s3':'Redeem','feat.s3p':'Staff scan a QR code on a tablet to confirm the reward',
 'feat.appT':'No app to install','feat.appP':'Opens in the browser and can be added to the home screen.',
 'feat.langT':'Three languages','feat.langP':'Customers choose their language.',
 'feat.giftT':'Rewards and referrals','feat.giftP':'Customers invite friends and both earn a bonus stamp.',
 'feat.g1':'Free coffee','feat.g1s':'after 6 stamps','feat.g2':'+1 stamp','feat.g2s':'for every referral',
 'feat.statT':'Know your customers','feat.statP':'Track visits, returning customers and rewards given. Send a notification or SMS to customers who haven’t visited in a while.',
 'demo.title':'Try TapStamp for free','demo.sub':'Fill in the form. We’ll get in touch and set up a demo for your business.',
 'demo.p1':'A ready page with your business name and colours','demo.p2':'Your first NFC chip is free','demo.p3':'We’ll contact you within one business day',
 'form.name':'Your name','form.phone':'Phone number','form.company':'Business name','form.type':'Type of business',
 'form.cafe':'Café','form.restaurant':'Restaurant','form.beauty':'Beauty salon','form.barber':'Barbershop','form.carwash':'Car wash','form.other':'Other',
 'form.branches':'Number of locations','form.many':'6 or more','form.note':'Notes','form.optional':'(optional)',
 'form.submit':'Send request','form.consent':'By sending this request, you agree that we use your details only to contact you.',
 'form.doneT':'Request received','form.doneP':'We’ll contact you within one business day.',
 'contact.title':'Have a question?','contact.sub':'Write to us or give us a call.','contact.email':'Email','contact.phone':'Phone',
 'footer.copy':'© 2026 Trend Solutions. All rights reserved.'
},
ru:{
 'form.sending':'Отправляем…','form.errLimit':'Слишком много заявок. Попробуйте через час.','form.errInvalid':'Проверьте поля: в номере телефона могут быть только цифры, пробелы и +.','form.errGeneric':'Заявка не отправлена. Попробуйте чуть позже или напишите на <a href="mailto:info@trendsolutions.az">info@trendsolutions.az</a>.',
 'nav.label':'Главное меню','nav.lang':'Язык','nav.services':'Услуги','nav.demo':'Демо','nav.contact':'Контакты',
 'hero.new':'Новинка','hero.pill':'TapStamp: цифровая карта лояльности с NFC',
 'hero.title':'Создаём <span class="grad">надёжное программное обеспечение</span> для вашего бизнеса',
 'hero.lead':'Мы — компания-разработчик программного обеспечения из Азербайджана. Разрабатываем веб- и мобильные приложения на заказ и создаём собственные продукты.',
 'hero.cta':'Обсудить проект','hero.cta2':'Наши услуги',
 'w.custom':'Разработка на заказ','w.customSub':'Веб, мобильные приложения и интеграции','w.tapSub':'Программа лояльности на основе NFC','w.next':'Новые продукты','w.nextSub':'Скоро',
 'dirs.kicker':'Чем мы занимаемся','dirs.title':'Работаем в двух направлениях',
 'dirs.customText':'Если готовые решения вам не подходят, мы вместе создадим систему под ваши задачи: веб- и мобильные приложения, панели управления, интеграции.','dirs.more':'Подробнее',
 'dirs.tapTitle':'Наш продукт: TapStamp','dirs.tapText':'Цифровая карта лояльности для кафе, салонов красоты и магазинов. Клиент подносит телефон к NFC-чипу, собирает штампы и получает подарки.','dirs.tapMore':'Узнать о продукте',
 'custom.title':'Если готовое решение не подходит, мы создадим ваше',
 'custom.sub':'Сначала мы готовим макеты всех экранов. После вашего утверждения переходим к разработке и показываем результат каждого этапа.',
 'custom.webT':'Веб-приложения и панели управления','custom.webP':'Системы заказов, складского учёта и управления клиентами (CRM).',
 'custom.mobT':'Мобильные приложения','custom.mobP':'Приложения для iPhone и Android, а также устанавливаемые веб-приложения.',
 'custom.intT':'Интеграции','custom.intP':'Платёжные системы, SMS-сервисы, кассовые программы и ваши текущие системы.',
 'custom.supT':'Поддержка и безопасность','custom.supP':'После запуска мы следим за серверами, обновлениями и резервными копиями.',
 'tap.kicker':'Наш продукт','tap.title':'Клиенты возвращаются <span class="grad">одним касанием</span>',
 'tap.lead':'NFC-чип и телефон заменяют бумажную карту со штампами. Клиенту не нужно устанавливать приложение.',
 'tap.cta':'Запросить бесплатное демо','tap.cta2':'Возможности',
 'card.company':'Кофейня','card.title':'Ваша карта','card.rule':'Каждый 6-й кофе в подарок',
 'card.tap':'Поднесите телефон к чипу','card.wait':'Ждём подтверждения сотрудника…','sticky.demo':'Демо','card.added':'Штамп добавлен','card.full':'Подарок готов! Начать заново',
 'card.caption':'Нажмите кнопку, чтобы увидеть, как добавляется штамп.',
 'feat.kicker':'Возможности TapStamp','feat.title':'Больше никаких поисков карты у кассы',
 'feat.sub':'Кафе, салон красоты, автомойка, магазин: если клиенты возвращаются к вам, TapStamp для вас.',
 'feat.tapT':'Штамп одним касанием','feat.tapP':'Клиент подносит телефон к чипу у кассы, и штамп автоматически добавляется на его карту. Чип создаёт новый код при каждом касании, поэтому подделать штамп невозможно.',
 'feat.s1':'Касается','feat.s1p':'На телефоне открывается страница вашей компании',
 'feat.s2':'Собирает штампы','feat.s2p':'Один раз регистрируется по номеру телефона, дальше каждое касание — штамп',
 'feat.s3':'Получает подарок','feat.s3p':'Сотрудник сканирует QR-код на планшете и подтверждает подарок',
 'feat.appT':'Без установки приложения','feat.appP':'Открывается в браузере, при желании добавляется на главный экран.',
 'feat.langT':'На трёх языках','feat.langP':'Клиент сам выбирает язык.',
 'feat.giftT':'Подарки и приглашения','feat.giftP':'Клиент приглашает друга, и оба получают бонусный штамп.',
 'feat.g1':'Бесплатный кофе','feat.g1s':'после 6 штампов','feat.g2':'+1 штамп','feat.g2s':'за каждое приглашение',
 'feat.statT':'Знайте своих клиентов','feat.statP':'Следите за визитами, возвращающимися клиентами и выданными подарками. Отправляйте уведомления или SMS тем, кто давно не заходил.',
 'demo.title':'Попробуйте TapStamp бесплатно','demo.sub':'Заполните форму. Мы свяжемся с вами и подготовим демо для вашей компании.',
 'demo.p1':'Готовая страница с названием и цветами вашей компании','demo.p2':'Первый NFC-чип бесплатно','demo.p3':'Свяжемся с вами в течение одного рабочего дня',
 'form.name':'Ваше имя','form.phone':'Номер телефона','form.company':'Название компании','form.type':'Сфера деятельности',
 'form.cafe':'Кафе','form.restaurant':'Ресторан','form.beauty':'Салон красоты','form.barber':'Барбершоп','form.carwash':'Автомойка','form.other':'Другое',
 'form.branches':'Количество филиалов','form.many':'6 и более','form.note':'Комментарий','form.optional':'(необязательно)',
 'form.submit':'Отправить заявку','form.consent':'Отправляя заявку, вы соглашаетесь, что мы используем ваши данные только для связи с вами.',
 'form.doneT':'Заявка принята','form.doneP':'Мы свяжемся с вами в течение одного рабочего дня.',
 'contact.title':'Есть вопросы?','contact.sub':'Напишите или позвоните нам.','contact.email':'Email','contact.phone':'Телефон',
 'footer.copy':'© 2026 Trend Solutions. Все права защищены.'
}};

let lang='az';
const t=k=>T[lang][k]??T.az[k];
function applyLang(l){
  lang=T[l]?l:'az';
  document.documentElement.lang=lang;
  document.querySelectorAll('[data-t]').forEach(el=>{el.innerHTML=t(el.dataset.t)});
  document.querySelectorAll('[data-t-aria]').forEach(el=>el.setAttribute('aria-label',t(el.dataset.tAria)));
  document.querySelectorAll('[data-lang]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.lang===lang)));
  label.textContent=t(n>=TOTAL?'card.full':n===start?'card.tap':'card.added');
  try{localStorage.setItem('ts-lang',lang)}catch(e){}
}
document.querySelectorAll('[data-lang]').forEach(b=>b.addEventListener('click',()=>applyLang(b.dataset.lang)));

// Demo kart: hər toxunuşda bir möhür; kart dolanda hədiyyə, sonra yenidən başlayır
const TOTAL=6, start=3; let n=start;
const slots=document.getElementById('slots'), count=document.getElementById('count'),
      label=document.getElementById('tapLabel'), card=document.getElementById('demoCard');
function render(fresh){
  slots.innerHTML='';
  for(let i=0;i<TOTAL;i++){
    const s=document.createElement('span');
    s.className='slot'+(i<n?' on':'')+(fresh&&i===n-1?' new':'');
    if(i>=n) s.textContent=i===TOTAL-1?'🎁':i+1;
    slots.appendChild(s);
  }
  count.textContent=n+'/'+TOTAL;
}
document.getElementById('tap').addEventListener('click',()=>{
  if(n>=TOTAL){n=0;render();label.textContent=t('card.tap');return;}
  if(card.classList.contains('wait')) return;
  // Əvvəl işçi təsdiqi gözlənilir, sonra möhür düşür (real məhsulda olduğu kimi)
  card.classList.add('wait'); label.textContent=t('card.wait');
  slots.children[n].classList.add('pending');
  setTimeout(()=>{
    card.classList.remove('wait');
    n++; render(true);
    card.classList.remove('pulse'); void card.offsetWidth; card.classList.add('pulse');
    label.textContent=t(n===TOTAL?'card.full':'card.added');
  },1600);
});
render();

let saved=null; try{saved=localStorage.getItem('ts-lang')}catch(e){}
applyLang(saved||(navigator.language||'az').slice(0,2));

// ===== Animasiyalar =====
const reduce=matchMedia('(prefers-reduced-motion: reduce)').matches;
const bar=document.createElement('div'); bar.className='progress'; document.body.prepend(bar);

// Hansı element necə görünür: [selektor, növ, qardaşlar arasında pillə-pillə gecikmə]
const REVEAL=[
  ['.pill','down'],['.lead,.ctas','up'],['.what .w-card','right',1],
  ['.kicker','left'],['.sub','up'],
  ['.dir','tilt',1],['.li','right',1],
  ['.p-logo','left'],['.demo','zoom'],
  ['.tile','up',1],['.steps div','up',1],
  ['.perks li','left',1],['form','zoom'],['.ci','up',1],['footer .wrap','up']
];
REVEAL.forEach(([sel,kind,stagger])=>document.querySelectorAll(sel).forEach((el,i)=>{
  el.classList.add('rv',kind); if(stagger) el.style.setProperty('--i',i);
}));

// Başlıqları sözlərə bölür (dil dəyişəndə yenidən)
function splitWords(){
  document.querySelectorAll('h1,h2').forEach(h=>{
    let k=0;
    const walk=node=>[...node.childNodes].forEach(c=>{
      if(c.nodeType===3){
        const frag=document.createDocumentFragment();
        c.textContent.split(/(\s+)/).forEach(part=>{
          if(!part) return;
          if(/^\s+$/.test(part)){frag.append(part);return;}
          const s=document.createElement('span'); s.className='w'; s.style.setProperty('--w',k++); s.textContent=part; frag.append(s);
        });
        c.replaceWith(frag);
      } else if(c.nodeType===1 && c.classList.contains('grad')){c.classList.add('w'); c.style.setProperty('--w',k++);}
      else if(c.nodeType===1 && !c.classList.contains('w')) walk(c);
    });
    walk(h); h.classList.add('words');
  });
}

// Görünmə iki müşahidəçi ilə: element ekrana girəndə (aşağıdan 12% içəri) .in əlavə olunur, ekrandan 120 px uzaqlaşanda silinir.
// Sərhədlər arasındakı boşluq animasiyanın öz sürüşməsindən (48–64 px) böyükdür, ona görə kənarda dayananda
// element "gəlib-getmir"; aşağı və yuxarı scroll-da animasiya yenə təkrar oynayır.
const showIO=new IntersectionObserver(es=>es.forEach(e=>{
  if(!e.isIntersecting || e.target.classList.contains('in')) return;
  e.target.classList.add('in');
  if(e.target.id==='demoCard') autoTap();
}),{threshold:0,rootMargin:'0px 0px -12% 0px'});
const hideIO=new IntersectionObserver(es=>es.forEach(e=>{
  if(!e.isIntersecting) e.target.classList.remove('in');
}),{threshold:0,rootMargin:'120px 0px 120px 0px'});
function observeAll(){document.querySelectorAll('.rv,.words').forEach(el=>{showIO.observe(el);hideIO.observe(el)})}

// Demo kart görünəndə bir möhür özü düşür
let autoT; function autoTap(){clearTimeout(autoT); autoT=setTimeout(()=>{if(n<TOTAL) document.getElementById('tap').click()},700)}

// Paralaks və irəliləyiş xətti
const PX=[...document.querySelectorAll('.glow,.what,.demo .phone,.chip')];
PX.forEach(el=>el.classList.add('px'));
const steps=[...document.querySelectorAll('.steps div')];
let ticking=false;
function onScroll(){
  if(ticking) return; ticking=true;
  requestAnimationFrame(()=>{
    const y=scrollY, max=document.documentElement.scrollHeight-innerHeight;
    bar.style.transform=`scaleX(${max>0?y/max:0})`;
    if(!reduce){
      PX.forEach(el=>{
        const r=el.getBoundingClientRect(), c=(r.top+r.height/2-innerHeight/2);
        const k=el.classList.contains('glow')?-.25:el.classList.contains('chip')?-.18:-.08;
        // Sürüşmə ±40 px ilə məhdudlaşır, telefonda yalnız fon ləkələri hərəkət edir — kartlar düymələrin üstünə çıxmasın
        const v=(innerWidth<600 && !el.classList.contains('glow'))?0:Math.max(-40,Math.min(40,c*k));
        el.style.setProperty('--py',v.toFixed(1)+'px');
      });
      // Böyük kafeldəki addımlar scroll-a görə növbə ilə işıqlanır
      const big=document.querySelector('.t-big');
      if(big){const r=big.getBoundingClientRect(), p=(innerHeight*.75-r.top)/r.height;
        steps.forEach((s,i)=>s.classList.toggle('lit',p>.35+i*.18 && r.bottom>0));}
    }
    ticking=false;
  });
}
addEventListener('scroll',onScroll,{passive:true});
// Telefonda yığcam zolaq: hero-dan aşağı düşəndə görünür
const mbar=document.querySelector('.mbar');
const onBar=()=>mbar.classList.toggle('show',scrollY>480);
addEventListener('scroll',onBar,{passive:true}); onBar(); addEventListener('resize',onScroll);

if(!reduce){splitWords(); observeAll();}
else document.querySelectorAll('.rv').forEach(el=>el.classList.add('in'));
onScroll();
// Dil dəyişəndə başlıqlar yenidən sözlərə bölünür
document.querySelectorAll('[data-lang]').forEach(b=>b.addEventListener('click',()=>{if(!reduce){splitWords();observeAll();}}));

// Demo müraciəti backend-ə göndərilir, oradan şirkətin poçt qutusuna email kimi çatır
const form=document.getElementById('demoForm'), formErr=document.getElementById('formErr');
const PHONE=/^[0-9+()\-\s]{5,30}$/;
form.addEventListener('submit',async e=>{
  e.preventDefault();
  const req=[...form.querySelectorAll('[required]')];
  req.forEach(i=>i.classList.toggle('err',!i.value.trim()));
  const phone=form.elements.phone;
  if(phone.value.trim() && !PHONE.test(phone.value.trim())) phone.classList.add('err');
  const bad=req.find(i=>i.classList.contains('err'));
  if(bad){bad.focus();return;}
  const btn=form.querySelector('button[type=submit]');
  btn.disabled=true; btn.textContent=t('form.sending'); formErr.hidden=true;
  const v=n=>form.elements[n].value.trim();
  try{
    const res=await fetch('/api/v1/site/demo-requests',{method:'POST',
      headers:{'Content-Type':'application/json','Accept':'application/json'},
      body:JSON.stringify({name:v('name'),phone:v('phone'),company:v('company'),businessType:form.elements.type.value,
        branches:form.elements.branches.value,note:v('note'),language:lang,website:form.elements.website.value})});
    if(res.status===204){
      form.innerHTML='<div class="done"><i><svg><use href="#check"/></svg></i><h3 data-t="form.doneT"></h3><p data-t="form.doneP"></p></div>';
      form.querySelectorAll('[data-t]').forEach(el=>{el.innerHTML=t(el.dataset.t)});
      return;
    }
    showErr(res.status===429?'form.errLimit':res.status===400?'form.errInvalid':'form.errGeneric');
  }catch(err){showErr('form.errGeneric');}
  btn.disabled=false; btn.textContent=t('form.submit');
});
function showErr(key){formErr.dataset.t=key; formErr.innerHTML=t(key); formErr.hidden=false;}

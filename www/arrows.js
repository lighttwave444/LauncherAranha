(function(){
  var A = window.Capacitor && Capacitor.Plugins && Capacitor.Plugins.Arrows;
  if(!A) return;
  var st=document.createElement('style');
  st.textContent='.nativearrows .drh{opacity:0!important}';
  document.head.appendChild(st);
  function send(){
    var d=window.devicePixelRatio||1;
    var items=[].slice.call(document.querySelectorAll('.drh')).map(function(h){
      var r=h.getBoundingClientRect();
      return {id:h.classList.contains('top')?'top':'bot',
        y:Math.round(r.top*d),w:Math.round(r.width*d),h:Math.round(r.height*d),
        on:h.classList.contains('on')};
    }).filter(function(i){return i.w>0&&i.h>0});
    A.sync({items:items,visible:!document.hidden})
      .then(function(){document.documentElement.classList.add('nativearrows')})
      .catch(function(){});
  }
  A.addListener('arrowTap',function(e){
    var h=document.querySelector('.drh.'+e.id);
    if(h){h.click();setTimeout(send,60);}
  });
  ['load','resize','orientationchange'].forEach(function(ev){
    window.addEventListener(ev,function(){setTimeout(send,200)});
  });
  document.addEventListener('visibilitychange',send);
  setTimeout(send,500);setTimeout(send,1500);
})();

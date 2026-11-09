(function(){
  var A = window.Capacitor && Capacitor.Plugins && Capacitor.Plugins.AppLauncher;
  var p = document.getElementById('ptr');
  if(!A || !p) return;
  var st = document.createElement('style');
  st.textContent = 'html.nptr #ptr{visibility:hidden!important}';
  document.head.appendChild(st);
  var d = window.devicePixelRatio || 1, last = '', lastDrag = '';
  var held = false, grabbing = false;

  function tip(){
    try{
      if(typeof px !== 'number' || typeof phone === 'undefined') return null;
      var r = phone.getBoundingClientRect();
      return {x:Math.round((r.left+px)*d), y:Math.round((r.top+py)*d)};
    }catch(e){ return null; }
  }
  window.__pgGrab = function(){
    var t = tip(); if(!t) return;
    held = true;
    A.grab(t).then(function(res){
      if(res && res.ok){
        if(held){ grabbing = true; lastDrag = ''; }
        else { A.drop().catch(function(){}); }
      }
    }).catch(function(){});
  };
  window.__pgDrop = function(){
    held = false; grabbing = false;
    A.drop().catch(function(){});
  };

  function tick(){
    var r = p.getBoundingClientRect();
    var o = {x:Math.round(r.left*d), y:Math.round(r.top*d),
             w:Math.round(r.width*d), h:Math.round(r.height*d),
             v:!document.hidden && r.width > 0};
    var k = [o.x,o.y,o.w,o.h,o.v].join();
    if(k !== last){
      last = k;
      A.ptr(o).then(function(){ document.documentElement.classList.add('nptr'); })
              .catch(function(){});
    }
    if(grabbing){
      var t = tip();
      if(t){
        var kk = t.x + ',' + t.y;
        if(kk !== lastDrag){ lastDrag = kk; A.dragTo(t).catch(function(){}); }
      }
    }
    requestAnimationFrame(tick);
  }
  tick();
})();

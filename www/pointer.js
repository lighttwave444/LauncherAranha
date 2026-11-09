(function(){
  function DPR(){return window.devicePixelRatio||1;}
  var A = window.Capacitor && Capacitor.Plugins && Capacitor.Plugins.AppLauncher;
  var p = document.getElementById('ptr');
  if(!A || !p) return;
  var st = document.createElement('style');
  st.textContent = 'html.nptr #ptr{visibility:hidden!important}';
  document.head.appendChild(st);
  var d = window.devicePixelRatio || 1, last = '', lastDrag = '';
  var held = false, grabbing = false;
  var clicking = false, lastClk = '';

  function tip(){
    try{
      if(typeof px !== 'number' || typeof phone === 'undefined') return null;
      var r = phone.getBoundingClientRect();
      return {x:Math.round((r.left+px)*DPR()), y:Math.round((r.top+py)*DPR())};
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
      else {
        A.ptrEvent({a:0, x:t.x, y:t.y}).then(function(r2){
          if(!(r2 && r2.ok)) return;
          if(held){ clicking = true; lastClk = t.x + ',' + t.y; }
          else { A.ptrEvent({a:1, x:t.x, y:t.y}).catch(function(){}); }
        }).catch(function(){});
      }
    }).catch(function(){});
  };
  window.__pgDrop = function(){
    held = false; grabbing = false;
    A.drop().catch(function(){});
    if(clicking){
      clicking = false;
      var t2 = tip();
      if(t2) A.ptrEvent({a:1, x:t2.x, y:t2.y}).catch(function(){});
    }
  };

  window.__pgTap = function(fallback){
    var t = tip();
    if(!t){ fallback(); return; }
    A.ptrEvent({a:0, x:t.x, y:t.y}).then(function(res){
      if(res && res.ok){
        setTimeout(function(){ A.ptrEvent({a:1, x:t.x, y:t.y}).catch(function(){}); }, 40);
      } else { fallback(); }
    }).catch(function(){ fallback(); });
  };

  function tick(){
    var r = p.getBoundingClientRect();
    var o = {x:Math.round(r.left*DPR()), y:Math.round(r.top*DPR()),
             w:Math.round(r.width*DPR()), h:Math.round(r.height*DPR()),
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
    if(clicking){
      var c = tip();
      if(c){
        var ck = c.x + ',' + c.y;
        if(ck !== lastClk){ lastClk = ck; A.ptrEvent({a:2, x:c.x, y:c.y}).catch(function(){}); }
      }
    }
    requestAnimationFrame(tick);
  }
  tick();
})();

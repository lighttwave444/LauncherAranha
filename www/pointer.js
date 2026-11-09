(function(){
  var A = window.Capacitor && Capacitor.Plugins && Capacitor.Plugins.AppLauncher;
  var p = document.getElementById('ptr');
  if(!A || !p) return;
  var st = document.createElement('style');
  st.textContent = 'html.nptr #ptr{visibility:hidden!important}';
  document.head.appendChild(st);
  var d = window.devicePixelRatio || 1, last = '';
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
    requestAnimationFrame(tick);
  }
  tick();
})();

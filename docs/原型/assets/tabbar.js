/* 底部 TabBar —— 4 个一级页面共用，避免各页抄写不一致
   实测（像素填充密度）：
     选中项 = 【实心】图标（房子 81.5% / 约课 79.5% / 已约 84.8% / 我的 63%）
     未选中 = 【描边】图标（40.1% / 56.4% / 47.7% / 39%）
     选中项颜色 #4799EF，但【文字始终是灰色 #717579】，不跟着变蓝。 */
(function () {
  // 未选中：描边
  var OUTLINE = {
    home: '<path d="M3.2 10.4 12 3.4l8.8 7"/><path d="M5.8 9.6V20.4h12.4V9.6"/><path d="M10 20.4v-4.6h4v4.6"/>',
    booking: '<rect x="3.6" y="5.2" width="16.8" height="15.6" rx="2.6"/><path d="M3.6 9.6h16.8M8.2 3.2v4M15.8 3.2v4"/><circle cx="12" cy="14.8" r="3.1"/><path d="M12 13.5v1.4l1 1"/>',
    booked: '<rect x="3.6" y="5.2" width="16.8" height="15.6" rx="2.6"/><path d="M3.6 9.6h16.8M8.2 3.2v4M15.8 3.2v4"/><path d="M9.2 15.1l2.1 2.1 4-4.4"/>',
    me: '<circle cx="12" cy="8.2" r="3.9"/><path d="M4.7 20.6c.5-4 3.5-6.2 7.3-6.2s6.8 2.2 7.3 6.2"/>'
  };
  // 选中：实心。日历经/勾用白色挖出细节（TabBar 底是纯白）
  var CAL = '<path d="M6.6 2.2h1.9v2.3h7V2.2h1.9v2.3h1.3c.9 0 1.7.8 1.7 1.7v13c0 .9-.8 1.7-1.7 1.7H5.3c-.9 0-1.7-.8-1.7-1.7v-13c0-.9.8-1.7 1.7-1.7h1.3V2.2Z"/>';
  var SOLID = {
    home: '<path d="M12 2.2 1.9 11.4v1.7h2.5v9.1h15.2v-9.1h2.5v-1.7L12 2.2Z"/>',
    booking: CAL +
      '<circle cx="12" cy="15" r="3.5" fill="#fff"/>' +
      '<path d="M12 13.2v1.9l1.3 1.3" fill="none" stroke="#fff" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>',
    booked: CAL +
      '<path d="M9.4 15.3l1.9 1.9 3.9-4.3" fill="none" stroke="#fff" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/>',
    me: '<circle cx="12" cy="8" r="4.1"/><path d="M12 13.5c-4.2 0-7.4 2.5-7.8 6.7h15.6c-.4-4.2-3.6-6.7-7.8-6.7Z"/>'
  };

  var TABS = [
    { key: 'home', label: '首页', href: 'home.html' },
    { key: 'booking', label: '约课', href: 'booking.html' },
    { key: 'booked', label: '已约', href: 'my-bookings.html' },
    { key: 'me', label: '我的', href: 'profile.html' }
  ];

  var SZ = 20;   // 实测图标 16x18pt

  function icon(key, active) {
    if (active) {
      return '<svg width="' + SZ + '" height="' + SZ + '" viewBox="0 0 24 24" fill="currentColor">' + SOLID[key] + '</svg>';
    }
    return '<svg width="' + SZ + '" height="' + SZ + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
      'stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">' + OUTLINE[key] + '</svg>';
  }

  window.renderTabBar = function (active) {
    var host = document.getElementById('tabbar');
    if (!host) return;
    host.className = 'tabbar';
    host.innerHTML = TABS.map(function (t) {
      var on = t.key === active;
      return '<a class="tab' + (on ? ' active' : '') + '" href="' + t.href + '">' +
        icon(t.key, on) + '<span class="tab-label">' + t.label + '</span></a>';
    }).join('');
  };
})();

define(['frameworks'], function() {
  var templates;
  templates = {};
  templates['index.js'] = (function(locals) {
  var __;
  __ = jade.init();
  with (locals || {}) {;

  __.buf.push('<div');
  __.buf.push(__.attrs({
    'class': 'container'
  }));
  __.buf.push('><script type="text/javascript">\n(function() {\n\n  alert("hi");\n\n}).call(this);\n</script></div>');
  };

  return __.buf.join("");
});
  return templates;
});

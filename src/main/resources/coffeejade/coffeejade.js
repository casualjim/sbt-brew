(function(){
// CommonJS require()

function require(p){
    var path = require.resolve(p)
      , mod = require.modules[path];
    if (!mod) throw new Error('failed to require "' + p + '"');
    if (!mod.exports) {
      mod.exports = {};
      mod.call(mod.exports, mod, mod.exports, require.relative(path));
    }
    return mod.exports;
  }

require.modules = {};

require.resolve = function (path){
    var orig = path
      , reg = path + '.js'
      , index = path + '/index.js';
    return require.modules[reg] && reg
      || require.modules[index] && index
      || orig;
  };

require.register = function (path, fn){
    require.modules[path] = fn;
  };

require.relative = function (parent) {
    return function(p){
      if ('.' != p[0]) return require(p);
      
      var path = parent.split('/')
        , segs = p.split('/');
      path.pop();
      
      for (var i = 0; i < segs.length; i++) {
        var seg = segs[i];
        if ('..' == seg) path.pop();
        else if ('.' != seg) path.push(seg);
      }

      return require(path.join('/'));
    };
  };


require.register("compiler.js", function(module, exports, require){

/*!
 * Jade - Compiler
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var nodes = require('./nodes')
  , filters = require('./filters')
  , doctypes = require('./doctypes')
  , selfClosing = require('./self-closing')
  , inlineTags = require('./inline-tags')
  , utils = require('./utils');

 
 if (!Object.keys) {
   Object.keys = function(obj){
     var arr = [];
     for (var key in obj) {
       if (obj.hasOwnProperty(key)) {
         arr.push(obj);
       }
     }
     return arr;
   } 
 }
 
 if (!String.prototype.trimLeft) {
   String.prototype.trimLeft = function(){
     return this.replace(/^\s+/, '');
   }
 }


/**
 * Initialize `Compiler` with the given `node`.
 *
 * @param {Node} node
 * @param {Object} options
 * @api public
 */

var Compiler = module.exports = function Compiler(node, options) {
  this.options = options = options || {};
  this.node = node;
  this.hasCompiledDoctype = false;
  this.hasCompiledTag = false;
  this.pp = options.pretty || false;
  this.indents = 0;
  if (options.doctype) this.setDoctype(options.doctype);
};

/**
 * Compiler prototype.
 */

Compiler.prototype = {
  
  /**
   * Compile parse tree to JavaScript.
   *
   * @api public
   */
  
  compile: function(){
    this.buf = [];
    this.indent = 0;
    this.smap = [];

    this.indented = function(value) {
      var b = ""
      for( i=0; i < this.indent; i++) {
        b += "  "
      }
      return b + value;
    };
    
    this.track_source = function(file, line, target_line) {
      file = file ? file : (this.smap.length > 0 ? this.smap[0].file : null);
      target_line = target_line ? target_line : this.buf.length+1
      this.smap.push({sf:file, sl:line, l:target_line});
    };
    
    this.p = function(value) {
      this.buf.push(this.indented(value))
    };
    
    this.block = function(next) {
      this.indent += 1;
      try {
        return next();
      } finally {
        this.lastBufferedIdx = -1;
        this.indent -= 1;
      }
    };
    
    this.lastBufferedIdx = -1
    this.p('(locals) ->')
    compiler = this;
    this.block(function(){
      compiler.p('__ = jade.init()')
      if(compiler.options.self) {
        compiler.p('self = locals || {}')
        compiler.visit(this.node);
      } else {
        compiler.p('`with (locals || {}) {`')
        compiler.visit(compiler.node);
        compiler.p('`}`')
      }
      compiler.p('__.buf.join("")')
    });
    return {code:this.buf.join('\n'), smap:this.smap};
  },

  /**
   * Sets the default doctype `name`. Sets terse mode to `true` when
   * html 5 is used, causing self-closing tags to end with ">" vs "/>",
   * and boolean attributes are not mirrored.
   *
   * @param {string} name
   * @api public
   */
  
  setDoctype: function(name){
    var doctype = doctypes[(name || 'default').toLowerCase()];
    doctype = doctype || '<!DOCTYPE ' + name + '>';
    this.doctype = doctype;
    this.terse = '5' == name || 'html' == name;
    this.xml = 0 == this.doctype.indexOf('<?xml');
  },
  
  /**
   * Buffer the given `str` optionally escaped.
   *
   * @param {String} str
   * @param {Boolean} esc
   * @api public
   */
  
  buffer: function(str, esc){
    if (esc) str = utils.escape(str);
    
    if (this.lastBufferedIdx == this.buf.length) {
      this.lastBuffered += str;
      this.buf[this.lastBufferedIdx - 1] = this.indented("__.buf.push('" + this.lastBuffered + "')");
    } else {
      this.p("__.buf.push('" + str + "')");
      this.lastBuffered = str;
      this.lastBufferedIdx = this.buf.length;
    }    
  },
  
  /**
   * Visit `node`.
   *
   * @param {Node} node
   * @api public
   */
  
  visit: function(node){
    this.track_source(node.filename, node.line)
    this.visitNode(node);
  },
  
  /**
   * Visit `node`.
   *
   * @param {Node} node
   * @api public
   */
  
  visitNode: function(node){
    var name = node.constructor.name
      || node.constructor.toString().match(/function ([^(\s]+)()/)[1];
    return this['visit' + name](node);
  },

  /**
   * Visit literal `node`.
   *
   * @param {Literal} node
   * @api public
   */

  visitLiteral: function(node){
    var str = node.str.replace(/\n/g, '\\\\n');
    this.buffer(str);
  },

  /**
   * Visit all nodes in `block`.
   *
   * @param {Block} block
   * @api public
   */

  visitBlock: function(block){
    var len = block.nodes.length;
    for (var i = 0; i < len; ++i) {
      this.visit(block.nodes[i]);
    }
  },
  
  /**
   * Visit `doctype`. Sets terse mode to `true` when html 5
   * is used, causing self-closing tags to end with ">" vs "/>",
   * and boolean attributes are not mirrored.
   *
   * @param {Doctype} doctype
   * @api public
   */
  
  visitDoctype: function(doctype){
    if (doctype && (doctype.val || !this.doctype)) {
      this.setDoctype(doctype.val || 'default');
    }

    if (this.doctype) this.buffer(this.doctype);
    this.hasCompiledDoctype = true;
  },

  /**
   * Visit `mixin`, generating a function that
   * may be called within the template.
   *
   * @param {Mixin} mixin
   * @api public
   */

  visitMixin: function(mixin){
    var name = mixin.name.replace(/-/g, '_') + '_mixin'
      , args = mixin.args || '';

    if (mixin.block) {
      this.p(name + ' = (' + args + ')->');
      compiler = this;
      compiler.block(function(){
        compiler.visit(mixin.block);
      });
    } else {
      this.p(name + '(' + args + ')');
    }
  },

  /**
   * Visit `tag` buffering tag markup, generating
   * attributes, visiting the `tag`'s code and block.
   *
   * @param {Tag} tag
   * @api public
   */
  
  visitTag: function(tag){
    this.indents++;
    var name = tag.name;

    if (!this.hasCompiledTag) {
      if (!this.hasCompiledDoctype && 'html' == name) {
        this.visitDoctype();
      }
      this.hasCompiledTag = true;
    }

    // pretty print
    if (this.pp && inlineTags.indexOf(name) == -1) {
      this.buffer('\\n' + Array(this.indents).join('  '));
    }

    if (~selfClosing.indexOf(name) && !this.xml) {
      this.buffer('<' + name);
      this.visitAttributes(tag.attrs);
      this.terse
        ? this.buffer('>')
        : this.buffer('/>');
    } else {
      // Optimize attributes buffering
      if (tag.attrs.length) {
        this.buffer('<' + name);
        if (tag.attrs.length) this.visitAttributes(tag.attrs);
        this.buffer('>');
      } else {
        this.buffer('<' + name + '>');
      }
      if (tag.code) this.visitCode(tag.code);
      if (tag.text) this.buffer(utils.text(tag.text.nodes[0].trimLeft()));
      this.escape = 'pre' == tag.name;
      this.visit(tag.block);

      // pretty print
      if (this.pp && !~inlineTags.indexOf(name) && !tag.textOnly) {
        this.buffer('\\n' + Array(this.indents).join('  '));
      }

      this.buffer('</' + name + '>');
    }
    this.indents--;
  },
  
  /**
   * Visit `filter`, throwing when the filter does not exist.
   *
   * @param {Filter} filter
   * @api public
   */
  
  visitFilter: function(filter){
    var fn = filters[filter.name];

    // unknown filter
    if (!fn) {
      if (filter.isASTFilter) {
        throw new Error('unknown ast filter "' + filter.name + ':"');
      } else {
        throw new Error('unknown filter ":' + filter.name + '"');
      }
    }
    if (filter.isASTFilter) {
      this.p(fn(filter.block, this, filter.attrs));
    } else {
      var text = filter.block.nodes.join('');
      this.buffer(utils.text(fn(text, filter.attrs)));
    }
  },
  
  /**
   * Visit `text` node.
   *
   * @param {Text} text
   * @api public
   */
  
  visitText: function(text){
    text = utils.text(text.nodes.join(''));
    if (this.escape) text = escape(text);
    this.buffer(text);
    this.buffer('\\n');
  },
  
  /**
   * Visit a `comment`, only buffering when the buffer flag is set.
   *
   * @param {Comment} comment
   * @api public
   */
  
  visitComment: function(comment){
    if (!comment.buffer) return;
    if (this.pp) this.buffer('\\n' + Array(this.indents + 1).join('  '));
    this.buffer('<!--' + utils.escape(comment.val) + '-->');
  },
  
  /**
   * Visit a `BlockComment`.
   *
   * @param {Comment} comment
   * @api public
   */
  
  visitBlockComment: function(comment){
    if (!comment.buffer) return;
    if (0 == comment.val.indexOf('if')) {
      this.buffer('<!--[' + comment.val + ']>');
      this.visit(comment.block);
      this.buffer('<![endif]-->');
    } else {
      this.buffer('<!--' + comment.val);
      this.visit(comment.block);
      this.buffer('-->');
    }
  },
  
  /**
   * Visit `code`, respecting buffer / escape flags.
   * If the code is followed by a block, wrap it in
   * a self-calling function.
   *
   * @param {Code} code
   * @api public
   */
  
  visitCode: function(code){
    // Wrap code blocks with {}.
    // we only wrap unbuffered code blocks ATM
    // since they are usually flow control

    // Buffer code
    var val = code.val.trimLeft();
    if (code.buffer) {
      if (code.escape) val = '__.escape(' + val + ')';
      this.p("__.buf.push(" + val + ");");
    } else {
      this.p(val);
    }

    // Block support
    if (code.block) {
      compiler = this;
      this.block(function(){
        compiler.visit(code.block);
      })
    }
  },
  
  /**
   * Visit `each` block.
   *
   * @param {Each} each
   * @api public
   */
  
  visitEach: function(each){
    this.p(''
      + '// iterate ' + each.obj + '\n'
      + '(function(){\n'
      + '  if (\'number\' == typeof ' + each.obj + '.length) {\n'
      + '    for (var ' + each.key + ' = 0, $$l = ' + each.obj + '.length; ' + each.key + ' < $$l; ' + each.key + '++) {\n'
      + '      var ' + each.val + ' = ' + each.obj + '[' + each.key + '];\n');

    this.visit(each.block);

    this.p(''
      + '    }\n'
      + '  } else {\n'
      + '    for (var ' + each.key + ' in ' + each.obj + ') {\n'
       + '      if (' + each.obj + '.hasOwnProperty(' + each.key + ')){'
      + '      var ' + each.val + ' = ' + each.obj + '[' + each.key + '];\n');

    this.visit(each.block);

     this.p('      }\n');

    this.p('   }\n  }\n}).call(this);\n');
  },
  
  /**
   * Visit `attrs`.
   *
   * @param {Array} attrs
   * @api public
   */
  
  visitAttributes: function(attrs){
    var buf = []
      , classes = [];

    if (this.terse) buf.push('terse: true');

    attrs.forEach(function(attr){
      if (attr.name == 'class') {
        var val = attr.val;
        if ( typeof(attr.val)!='string' ) {
          val = attr.val.join(")+' '+(");
        }
        classes.push('(' + val + ')');
      } else {
        var val = attr.val;
        if ( typeof(attr.val)!='string' ) {
          val = attr.val[0] 
        }
        var pair = "'" + attr.name + "':(" + val + ')';
        buf.push(pair);
      }
    });

    if (classes.length) {
      classes = classes.join(" + ' ' + ");
      buf.push("'class': " + classes);
    }
    
    buf = buf.join(', ');

    this.p("__.buf.push(__.attrs({ " + buf + " }));");
  }
};

/**
 * Escape the given string of `html`.
 *
 * @param {String} html
 * @return {String}
 * @api private
 */

function escape(html){
  return String(html)
    .replace(/&(?!\w+;)/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

}); // module: compiler.js

require.register("doctypes.js", function(module, exports, require){

/*!
 * Jade - doctypes
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

module.exports = {
    '5': '<!DOCTYPE html>'
  , 'xml': '<?xml version="1.0" encoding="utf-8" ?>'
  , 'default': '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">'
  , 'transitional': '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">'
  , 'strict': '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">'
  , 'frameset': '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Frameset//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd">'
  , '1.1': '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">'
  , 'basic': '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML Basic 1.1//EN" "http://www.w3.org/TR/xhtml-basic/xhtml-basic11.dtd">'
  , 'mobile': '<!DOCTYPE html PUBLIC "-//WAPFORUM//DTD XHTML Mobile 1.2//EN" "http://www.openmobilealliance.org/tech/DTD/xhtml-mobile12.dtd">'
};
}); // module: doctypes.js

require.register("filters.js", function(module, exports, require){

/*!
 * Jade - filters
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

module.exports = {
  
  /**
   * Wrap text with CDATA block.
   */
  
  cdata: function(str){
    return '<![CDATA[\\n' + str + '\\n]]>';
  },
  
  /**
   * Transform sass to css, wrapped in style tags.
   */
  
  sass: function(str){
    str = str.replace(/\\n/g, '\n');
    var sass = require('sass').render(str).replace(/\n/g, '\\n');
    return '<style>' + sass + '</style>'; 
  },
  
  /**
   * Transform stylus to css, wrapped in style tags.
   */
  
  stylus: function(str, options){
    var ret;
    str = str.replace(/\\n/g, '\n');
    var stylus = require('stylus');
    stylus(str, options).render(function(err, css){
      if (err) throw err;
      ret = css.replace(/\n/g, '\\n');
    });
    return '<style>' + ret + '</style>'; 
  },
  
  /**
   * Transform sass to css, wrapped in style tags.
   */
  
  less: function(str){
    var ret;
    str = str.replace(/\\n/g, '\n');
    require('less').render(str, function(err, css){
      if (err) throw err;
      ret = '<style>' + css.replace(/\n/g, '\\n') + '</style>';  
    });
    return ret;
  },
  
  /**
   * Transform markdown to html.
   */
  
  markdown: function(str){
    var md;

    // support markdown / discount
    try {
      md = require('markdown');
    } catch (err){
      try {
        md = require('discount');
      } catch (err) {
        try {
          md = require('markdown-js');
        } catch (err) {
          throw new Error('Cannot find markdown library, install markdown or discount');
        }
      }
    }

    str = str.replace(/\\n/g, '\n');
    return md.parse(str).replace(/\n/g, '\\n').replace(/'/g,'&#39;');
  },
  
  /**
   * Transform coffeescript to javascript.
   */

  coffeescript: function(str){
    str = str.replace(/\\n/g, '\n');
    var coffee = typeof(CoffeeScript) == 'undefined' ? require('coffee-script') : CoffeeScript;
    var js = coffee.compile(str).replace(/\n/g, '\\n');
    return '<script type="text/javascript">\\n' + js + '</script>';
  }
};
}); // module: filters.js

require.register("inline-tags.js", function(module, exports, require){

/*!
 * Jade - inline tags
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

module.exports = [
    'a'
  , 'abbr'
  , 'acronym'
  , 'b'
  , 'br'
  , 'code'
  , 'em'
  , 'font'
  , 'i'
  , 'img'
  , 'ins'
  , 'kbd'
  , 'map'
  , 'samp'
  , 'small'
  , 'span'
  , 'strong'
  , 'sub'
  , 'sup'
];
}); // module: inline-tags.js

require.register("jade.js", function(module, exports, require){

/*!
 * Jade
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var Parser = require('./parser')
  , Compiler = require('./compiler')
  , runtime = require('./runtime')

/**
 * Library version.
 */
exports.version = '1.0.0';

/**
 * Expose self closing tags.
 */

exports.selfClosing = require('./self-closing');

/**
 * Default supported doctypes.
 */

exports.doctypes = require('./doctypes');

/**
 * Text filters.
 */

exports.filters = require('./filters');

/**
 * Utilities.
 */

exports.utils = require('./utils');

/**
 * Expose `Compiler`.
 */

exports.Compiler = Compiler;

/**
 * Expose `Parser`.
 */

exports.Parser = Parser;

/**
 * Nodes.
 */

exports.nodes = require('./nodes');

/**
 * Jade runtime helpers.
 */

exports.runtime = runtime;

exports.compile = function(str, options){
  options = options || {}
  var filename = options.filename ? JSON.stringify(options.filename) : 'undefined'
  var fn;

  try {
    // Parse
    var parser = new Parser(str, options.filename, options);

    // Compile
    var compiler = new (options.compiler || Compiler)(parser.parse(), options)
    var result = compiler.compile();

    // Debug compiler
    if (options.debug) {
      console.error('\nGenerated CoffeeScript:\n\n\033[90m%s\033[0m', result.code.replace(/^/gm, '  '));
    }
    
    return result;
  } catch (err) {
    parser = parser.context();
    runtime.rethrow(err, parser.filename, parser.lexer.lineno);
  }

};

/**
 */
exports.compile_to_js = function(str, options){
  options = options || {}
  var coffee = typeof(CoffeeScript) == 'undefined' ? require('coffee-script') : CoffeeScript;
  var fn = exports.compile(str, options).code
  fn = coffee.compile(fn, {bare:true, filename:options.filename})
  if (options.debug) {
    console.error('\nGenerated JavaScript:\n\n\033[90m%s\033[0m', fn.replace(/^/gm, '  '));
  }
  return fn;
};

exports.template = function(str, options){
  options = options || {}
  var fn = exports.compile_to_js(str, options)
  fn = new Function('locals, jade', "var fn="+fn+"; return fn(locals);");
  return function(locals){
    return fn(locals, runtime);
  };
};
}); // module: jade.js

require.register("lexer.js", function(module, exports, require){

/*!
 * Jade - Lexer
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Initialize `Lexer` with the given `str`.
 *
 * Options:
 *
 * @param {String} str
 * @param {Object} options
 * @api private
 */
var utils = require('./utils');

var Lexer = module.exports = function Lexer(str, options) {
  options = options || {};
  this.input = str.replace(/\r\n|\r/g, '\n');
  this.deferredTokens = [];
  this.lastIndents = 0;
  this.lineno = 1;
  this.stash = [];
  this.indentStack = [];
  this.indentRe = null;
  this.pipeless = false;
};

/**
 * Lexer prototype.
 */

Lexer.prototype = {
  
  /**
   * Construct a token with the given `type` and `val`.
   *
   * @param {String} type
   * @param {String} val
   * @return {Object}
   * @api private
   */
  
  tok: function(type, val){
    return {
        type: type
      , line: this.lineno
      , val: val
    }
  },
  
  /**
   * Consume the given `len` of input.
   *
   * @param {Number} len
   * @api private
   */
  
  consume: function(len){
    this.input = this.input.substr(len);
  },
  
  /**
   * Scan for `type` with the given `regexp`.
   *
   * @param {String} type
   * @param {RegExp} regexp
   * @return {Object}
   * @api private
   */
  
  scan: function(regexp, type){
    var captures;
    if (captures = regexp.exec(this.input)) {
      this.consume(captures[0].length);
      return this.tok(type, captures[1]);
    }
  },
  
  /**
   * Defer the given `tok`.
   *
   * @param {Object} tok
   * @api private
   */
  
  defer: function(tok){
    this.deferredTokens.push(tok);
  },
  
  /**
   * Lookahead `n` tokens.
   *
   * @param {Number} n
   * @return {Object}
   * @api private
   */
  
  lookahead: function(n){
    var fetch = n - this.stash.length;
    while (fetch-- > 0) this.stash.push(this.next());
    return this.stash[--n];
  },
  
  /**
   * Return the indexOf `start` / `end` delimiters.
   *
   * @param {String} start
   * @param {String} end
   * @return {Number}
   * @api private
   */
  
  indexOfDelimiters: function(start, end){
    var str = this.input
      , nstart = 0
      , nend = 0
      , pos = 0;
    for (var i = 0, len = str.length; i < len; ++i) {
      if (start == str[i]) {
        ++nstart;
      } else if (end == str[i]) {
        if (++nend == nstart) {
          pos = i;
          break;
        }
      }
    }
    return pos;
  },
  
  /**
   * Stashed token.
   */
  
  stashed: function() {
    return this.stash.length
      && this.stash.shift();
  },
  
  /**
   * Deferred token.
   */
  
  deferred: function() {
    return this.deferredTokens.length 
      && this.deferredTokens.shift();
  },
  
  /**
   * end-of-source.
   */
  
  eos: function() {
    if (this.input.length) return;
    if (this.indentStack.length) {
      this.indentStack.shift();
      return this.tok('outdent');
    } else {
      return this.tok('eos');
    }
  },

  /**
   * Comment.
   */
  
  comment: function() {
    var captures;
    if (captures = /^ *\/\/(-)?([^\n]*)/.exec(this.input)) {
      this.consume(captures[0].length);
      var tok = this.tok('comment', captures[2]);
      tok.buffer = '-' != captures[1];
      return tok;
    }
  },
  
  /**
   * Tag.
   */
  
  tag: function() {
    var captures;
    if (captures = /^(\w[-:\w]*)/.exec(this.input)) {
      this.consume(captures[0].length);
      var tok, name = captures[1];
      if (':' == name[name.length - 1]) {
        name = name.slice(0, -1);
        tok = this.tok('tag', name);
        this.deferredTokens.push(this.tok(':'));
        while (' ' == this.input[0]) this.input = this.input.substr(1);
      } else {
        tok = this.tok('tag', name);
      }
      return tok;
    }
  },
  
  /**
   * Filter.
   */
  
  filter: function() {
    return this.scan(/^:(\w+)/, 'filter');
  },
  
  /**
   * Doctype.
   */
  
  doctype: function() {
    return this.scan(/^(?:!!!|doctype) *([^\n]+)?/, 'doctype');
  },

  /**
   * Id.
   */
  
  id: function() {
    return this.scan(/^#([\w-]+)/, 'id');
  },
  
  /**
   * Class.
   */
  
  className: function() {
    return this.scan(/^\.([\w-]+)/, 'class');
  },
  
  /**
   * Text.
   */
  
  text: function() {
    return this.scan(/^(?:\| ?)?([^\n]+)/, 'text');
  },

  /**
   * Extends.
   */
  
  extends: function() {
    return this.scan(/^extends +([^\n]+)/, 'extends');
  },

  /**
   * Block.
   */
  
  block: function() {
    return this.scan(/^block +([^\n]+)/, 'block');
  },

  /**
   * Include.
   */
  
  include: function() {
    return this.scan(/^include +([^\n]+)/, 'include');
  },

  

  /**
   * Mixin.
   */

  mixin: function(){
    var captures;
    if (captures = /^mixin +([-\w]+)(?:\(([^\)]+)\))?/.exec(this.input)) {
      this.consume(captures[0].length);
      var tok = this.tok('mixin', captures[1]);
      tok.args = captures[2];
      return tok;
    }
  },

  /**
   * Code.
   */
  
  code: function() {
    var captures;
    if (captures = /^(!?=|-)([^\n]+)/.exec(this.input)) {
      this.consume(captures[0].length);
      var flags = captures[1];
      captures[1] = captures[2];
      var tok = this.tok('code', captures[1]);
      tok.escape = flags[0] === '=';
      tok.buffer = flags[0] === '=' || flags[1] === '=';
      return tok;
    }
  },
  
  /**
   * Attributes.
   */
  
  attrs: function() {
    if ('(' == this.input[0]) {
      var index = this.indexOfDelimiters('(', ')')
        , str = this.input.substr(1, index-1)
        , tok = this.tok('attrs')
        , len = str.length
        , states = ['key']
        , key = ''
        , val = ''
        , quote
        , c;

      function state(){
        return states[states.length - 1];
      }

      this.consume(index + 1);
      tok.attrs = {};
      
      var string_start = 0

      var braces = {
        '[':']',
        '{':'}',
        '(':')'
      };
      
      var save_pair = function() {
        states.push('key');
        key = key.trim();
        if ('' == key) return;
        key = key.replace(/^['"]|['"]$/g, '');
        val = val.trim();
        if( val == '' ) {
          val = true;
        }
        
        old_val = tok.attrs[key];
        if( old_val ) { 
          if( typeof(old_val)=='string' ) { 
            // convert it to an array.
            old_val = [old_val];
            tok.attrs[key] = old_val;
          }
          old_val.push(val)
        } else {
          tok.attrs[key] = val;
        }
        key = val = '';
      }
      
      var start_string = function(c) {
        states.push('string');
        string_start = val.length;
        quote = c;
      }


      function parse(c) {
        
        switch (state()) {
          case 'key':
            switch (c) {
              case '\n': save_pair(); break; // ODD case. perhaps we should error
              case '=': states.push('val'); break;
              default: key += c;
            }
            break;
            
          case 'key char':
            switch (c) {
              case '\n': save_pair(); break; // ODD case. perhaps we should error
              case "'": states.pop(); break;
              default: key += c;
            }
            break;
            
          case 'val':
            switch (c) {
              case '\t':
              case ' ':
                if( val.trim().length > 0  ) {
                  save_pair();
                }
                break;
              case ',':
              case '\n': save_pair(); break;
              case '"':
              case "'": start_string(c); break;
              case '(':
              case '{':
              case '[': states.push(c); // yes, don't break.
              default: val += c;
            }
            break;
            
          case 'string':
            switch (c) {
              case '\n': val += quote; save_pair(); break; // Perhaps we should encode the \n instead.
              case '"':
              case "'":
                if (c == quote) {
                  states.pop();
                  val = val.substring(0, string_start)+"'"+utils.text(val.substring(string_start))+"'";
                  break;
                } // else don't break.
              default:
                val += c;
            }
            break;

          case '[':
          case '{':
          case '(':
            switch (c) {
              case '"':
              case "'": start_string(c); break;
              
              case '(':
              case '{':
              case '[': states.push(c); // yes, don't break.
              default:
                if( braces[state()] == c ) {
                  states.pop();
                }
                val += c;
            }
            break;
            
          default:
            throw "Uknonwn state: "+state();
        }
      }

      for (var i = 0; i < len; ++i) {
        parse(str[i]);
      }

      parse(',');

      return tok;
    }
  },
  
  /**
   * Indent | Outdent | Newline.
   */
  
  indent: function() {
    var captures, re;

    // established regexp
    if (this.indentRe) {
      captures = this.indentRe.exec(this.input);
    // determine regexp
    } else {
      // tabs
      re = /^\n(\t*) */;
      captures = re.exec(this.input);

      // spaces
      if (captures && !captures[1].length) {
        re = /^\n( *)/;
        captures = re.exec(this.input);
      }

      // established
      if (captures && captures[1].length) this.indentRe = re;
    }

    if (captures) {
      var tok
        , indents = captures[1].length;

      ++this.lineno;
      this.consume(indents + 1);

      if (' ' == this.input[0] || '\t' == this.input[0]) {
        throw new Error('Invalid indentation, you can use tabs or spaces but not both');
      }

      // blank line
      if ('\n' == this.input[0]) return this.tok('newline');

      // outdent
      if (this.indentStack.length && indents < this.indentStack[0]) {
        while (this.indentStack.length && this.indentStack[0] > indents) {
          this.stash.push(this.tok('outdent'));
          this.indentStack.shift();
        }
        tok = this.stash.pop();
      // indent
      } else if (indents && indents != this.indentStack[0]) {
        this.indentStack.unshift(indents);
        tok = this.tok('indent', indents);
      // newline
      } else {
        tok = this.tok('newline');
      }

      return tok;
    }
  },

  /**
   * Pipe-less text consumed only when 
   * pipeless is true;
   */

  pipelessText: function() {
    if (this.pipeless) {
      if ('\n' == this.input[0]) return;
      var i = this.input.indexOf('\n');
      if (-1 == i) i = this.input.length;
      var str = this.input.substr(0, i);
      this.consume(str.length);
      return this.tok('text', str);
    }
  },

  /**
   * ':'
   */

  colon: function() {
    return this.scan(/^: */, ':');
  },

  /**
   * Return the next token object, or those
   * previously stashed by lookahead.
   *
   * @return {Object}
   * @api private
   */
  
  advance: function(){
    return this.stashed()
      || this.next();
  },
  
  /**
   * Return the next token object.
   *
   * @return {Object}
   * @api private
   */
  
  next: function() {
    return this.deferred()
      || this.eos()
      || this.pipelessText()
      || this.doctype()
      || this.extends()
      || this.block()
      || this.include()
      || this.mixin()
      || this.tag()
      || this.filter()
      || this.code()
      || this.id()
      || this.className()
      || this.attrs()
      || this.indent()
      || this.comment()
      || this.colon()
      || this.text();
  }
};

}); // module: lexer.js

require.register("nodes/block-comment.js", function(module, exports, require){

/*!
 * Jade - nodes - BlockComment
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var Node = require('./node');

/**
 * Initialize a `BlockComment` with the given `block`.
 *
 * @param {String} val
 * @param {Block} block
 * @param {Boolean} buffer
 * @api public
 */

var BlockComment = module.exports = function BlockComment(val, block, buffer) {
  this.block = block;
  this.val = val;
  this.buffer = buffer;
};

/**
 * Inherit from `Node`.
 */

BlockComment.prototype = new Node;
BlockComment.prototype.constructor = BlockComment;

}); // module: nodes/block-comment.js

require.register("nodes/block.js", function(module, exports, require){

/*!
 * Jade - nodes - Block
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var Node = require('./node');

/**
 * Initialize a new `Block` with an optional `node`.
 *
 * @param {Node} node
 * @api public
 */

var Block = module.exports = function Block(node){
  this.nodes = [];
  if (node) this.push(node);
};

/**
 * Inherit from `Node`.
 */

Block.prototype = new Node;
Block.prototype.constructor = Block;


/**
 * Replace the nodes in `other` with the nodes
 * in `this` block.
 *
 * @param {Block} other
 * @api private
 */

Block.prototype.replace = function(other){
  other.nodes = this.nodes;
};

/**
 * Pust the given `node`.
 *
 * @param {Node} node
 * @return {Number}
 * @api public
 */

Block.prototype.push = function(node){
  return this.nodes.push(node);
};

/**
 * Unshift the given `node`.
 *
 * @param {Node} node
 * @return {Number}
 * @api public
 */

Block.prototype.unshift = function(node){
  return this.nodes.unshift(node);
};

}); // module: nodes/block.js

require.register("nodes/code.js", function(module, exports, require){

/*!
 * Jade - nodes - Code
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var Node = require('./node');

/**
 * Initialize a `Code` node with the given code `val`.
 * Code may also be optionally buffered and escaped.
 *
 * @param {String} val
 * @param {Boolean} buffer
 * @param {Boolean} escape
 * @api public
 */

var Code = module.exports = function Code(val, buffer, escape) {
  this.val = val;
  this.buffer = buffer;
  this.escape = escape;
  if (val.match(/^ *else/)) this.debug = false;
};

/**
 * Inherit from `Node`.
 */

Code.prototype = new Node;
Code.prototype.constructor = Code;

}); // module: nodes/code.js

require.register("nodes/comment.js", function(module, exports, require){

/*!
 * Jade - nodes - Comment
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var Node = require('./node');

/**
 * Initialize a `Comment` with the given `val`, optionally `buffer`,
 * otherwise the comment may render in the output.
 *
 * @param {String} val
 * @param {Boolean} buffer
 * @api public
 */

var Comment = module.exports = function Comment(val, buffer) {
  this.val = val;
  this.buffer = buffer;
};

/**
 * Inherit from `Node`.
 */

Comment.prototype = new Node;
Comment.prototype.constructor = Comment;

}); // module: nodes/comment.js

require.register("nodes/doctype.js", function(module, exports, require){

/*!
 * Jade - nodes - Doctype
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var Node = require('./node');

/**
 * Initialize a `Doctype` with the given `val`. 
 *
 * @param {String} val
 * @api public
 */

var Doctype = module.exports = function Doctype(val) {
  this.val = val;
};

/**
 * Inherit from `Node`.
 */

Doctype.prototype = new Node;
Doctype.prototype.constructor = Doctype;

}); // module: nodes/doctype.js

require.register("nodes/each.js", function(module, exports, require){

/*!
 * Jade - nodes - Each
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var Node = require('./node');

/**
 * Initialize an `Each` node, representing iteration
 *
 * @param {String} obj
 * @param {String} val
 * @param {String} key
 * @param {Block} block
 * @api public
 */

var Each = module.exports = function Each(obj, val, key, block) {
  this.obj = obj;
  this.val = val;
  this.key = key;
  this.block = block;
};

/**
 * Inherit from `Node`.
 */

Each.prototype = new Node;
Each.prototype.constructor = Each;

}); // module: nodes/each.js

require.register("nodes/filter.js", function(module, exports, require){

/*!
 * Jade - nodes - Filter
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var Node = require('./node')
  , Block = require('./block');

/**
 * Initialize a `Filter` node with the given 
 * filter `name` and `block`.
 *
 * @param {String} name
 * @param {Block|Node} block
 * @api public
 */

var Filter = module.exports = function Filter(name, block, attrs) {
  this.name = name;
  this.block = block;
  this.attrs = attrs;
  this.isASTFilter = block instanceof Block;
};

/**
 * Inherit from `Node`.
 */

Filter.prototype = new Node;
Filter.prototype.constructor = Filter;

}); // module: nodes/filter.js

require.register("nodes/index.js", function(module, exports, require){

/*!
 * Jade - nodes
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

exports.Node = require('./node');
exports.Tag = require('./tag');
exports.Code = require('./code');
exports.Each = require('./each');
exports.Text = require('./text');
exports.Block = require('./block');
exports.Mixin = require('./mixin');
exports.Filter = require('./filter');
exports.Comment = require('./comment');
exports.Literal = require('./literal');
exports.BlockComment = require('./block-comment');
exports.Doctype = require('./doctype');

}); // module: nodes/index.js

require.register("nodes/literal.js", function(module, exports, require){

/*!
 * Jade - nodes - Literal
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var Node = require('./node');

/**
 * Initialize a `Literal` node with the given `str.
 *
 * @param {String} str
 * @api public
 */

var Literal = module.exports = function Literal(str) {
  this.str = str;
};

/**
 * Inherit from `Node`.
 */

Literal.prototype = new Node;
Literal.prototype.constructor = Literal;


}); // module: nodes/literal.js

require.register("nodes/mixin.js", function(module, exports, require){

/*!
 * Jade - nodes - Mixin
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var Node = require('./node');

/**
 * Initialize a new `Mixin` with `name` and `block`.
 *
 * @param {String} name
 * @param {String} args
 * @param {Block} block
 * @api public
 */

var Mixin = module.exports = function Mixin(name, args, block){
  this.name = name;
  this.args = args;
  this.block = block;
};

/**
 * Inherit from `Node`.
 */

Mixin.prototype = new Node;
Mixin.prototype.constructor = Mixin;



}); // module: nodes/mixin.js

require.register("nodes/node.js", function(module, exports, require){

/*!
 * Jade - nodes - Node
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Initialize a `Node`.
 *
 * @api public
 */

var Node = module.exports = function Node(){};
}); // module: nodes/node.js

require.register("nodes/tag.js", function(module, exports, require){

/*!
 * Jade - nodes - Tag
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var Node = require('./node'),
    Block = require('./block');

/**
 * Initialize a `Tag` node with the given tag `name` and optional `block`.
 *
 * @param {String} name
 * @param {Block} block
 * @api public
 */

var Tag = module.exports = function Tag(name, block) {
  this.name = name;
  this.attrs = [];
  this.block = block || new Block;
};

/**
 * Inherit from `Node`.
 */

Tag.prototype = new Node;
Tag.prototype.constructor = Tag;


/**
 * Set attribute `name` to `val`, keep in mind these become
 * part of a raw js object literal, so to quote a value you must
 * '"quote me"', otherwise or example 'user.name' is literal JavaScript.
 *
 * @param {String} name
 * @param {String} val
 * @return {Tag} for chaining
 * @api public
 */

Tag.prototype.setAttribute = function(name, val){
  this.attrs.push({ name: name, val: val });
  return this;
};

/**
 * Remove attribute `name` when present.
 *
 * @param {String} name
 * @api public
 */

Tag.prototype.removeAttribute = function(name){
  for (var i = 0, len = this.attrs.length; i < len; ++i) {
    if (this.attrs[i] && this.attrs[i].name == name) {
      delete this.attrs[i];
    }
  }
};

/**
 * Get attribute value by `name`.
 *
 * @param {String} name
 * @return {String}
 * @api public
 */

Tag.prototype.getAttribute = function(name){
  for (var i = 0, len = this.attrs.length; i < len; ++i) {
    if (this.attrs[i] && this.attrs[i].name == name) {
      return this.attrs[i].val;
    }
  }
};

}); // module: nodes/tag.js

require.register("nodes/text.js", function(module, exports, require){

/*!
 * Jade - nodes - Text
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var Node = require('./node');

/**
 * Initialize a `Text` node with optional `line`.
 *
 * @param {String} line
 * @api public
 */

var Text = module.exports = function Text(line) {
  this.nodes = [];
  if ('string' == typeof line) this.push(line);
};

/**
 * Inherit from `Node`.
 */

Text.prototype = new Node;
Text.prototype.constructor = Text;


/**
 * Push the given `node.`
 *
 * @param {Node} node
 * @return {Number}
 * @api public
 */

Text.prototype.push = function(node){
  return this.nodes.push(node);
};

}); // module: nodes/text.js

require.register("parser.js", function(module, exports, require){

/*!
 * Jade - Parser
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Module dependencies.
 */

var Lexer = require('./lexer')
  , nodes = require('./nodes');

/**
 * Initialize `Parser` with the given input `str` and `filename`.
 *
 * @param {String} str
 * @param {String} filename
 * @param {Object} options
 * @api public
 */

var Parser = exports = module.exports = function Parser(str, filename, options){
  this.input = str;
  this.lexer = new Lexer(str, options);
  this.filename = filename;
  this.blocks = {};
  this.contexts = [this];
};

/**
 * Tags that may not contain tags.
 */

var textOnly = exports.textOnly = ['code', 'script', 'textarea', 'style', 'title'];

/**
 * Parser prototype.
 */

Parser.prototype = {

  /**
   * Push `parser` onto the context stack,
   * or pop and return a `Parser`.
   */

  context: function(parser){
    if (parser) {
      this.contexts.push(parser);
    } else {
      return this.contexts.pop();
    }
  },

  /**
   * Return the next token object.
   *
   * @return {Object}
   * @api private
   */

  advance: function(){
    return this.lexer.advance();
  },

  /**
   * Skip `n` tokens.
   *
   * @param {Number} n
   * @api private
   */

  skip: function(n){
    while (n--) this.advance();
  },
  
  /**
   * Single token lookahead.
   *
   * @return {Object}
   * @api private
   */
  
  peek: function() {
    return this.lookahead(1);
  },
  
  /**
   * Return lexer lineno.
   *
   * @return {Number}
   * @api private
   */
  
  line: function() {
    return this.lexer.lineno;
  },
  
  /**
   * `n` token lookahead.
   *
   * @param {Number} n
   * @return {Object}
   * @api private
   */
  
  lookahead: function(n){
    return this.lexer.lookahead(n);
  },
  
  /**
   * Parse input returning a string of js for evaluation.
   *
   * @return {String}
   * @api public
   */
  
  parse: function(){
    var block = new nodes.Block, parser;
    block.line = this.line();

    while ('eos' != this.peek().type) {
      if ('newline' == this.peek().type) {
        this.advance();
      } else {
        block.push(this.parseExpr());
      }
    }

    if (parser = this.extending) {
      this.context(parser);
      var ast = parser.parse();
      this.context();
      return ast;
    }

    return block;
  },
  
  /**
   * Expect the given type, or throw an exception.
   *
   * @param {String} type
   * @api private
   */
  
  expect: function(type){
    if (this.peek().type === type) {
      return this.advance();
    } else {
      throw new Error('expected "' + type + '", but got "' + this.peek().type + '"');
    }
  },
  
  /**
   * Accept the given `type`.
   *
   * @param {String} type
   * @api private
   */
  
  accept: function(type){
    if (this.peek().type === type) {
      return this.advance();
    }
  },
  
  /**
   *   tag
   * | doctype
   * | mixin
   * | include
   * | filter
   * | comment
   * | text
   * | each
   * | code
   * | id
   * | class
   */
  
  parseExpr: function(){
    switch (this.peek().type) {
      case 'tag':
        return this.parseTag();
      case 'mixin':
        return this.parseMixin();
      case 'block':
        return this.parseBlock();
      case 'extends':
        return this.parseExtends();
      case 'include':
        return this.parseInclude();
      case 'doctype':
        return this.parseDoctype();
      case 'filter':
        return this.parseFilter();
      case 'comment':
        return this.parseComment();
      case 'text':
        return this.parseText();
      case 'each':
        return this.parseEach();
      case 'code':
        return this.parseCode();
      case 'id':
      case 'class':
        var tok = this.advance();
        this.lexer.defer(this.lexer.tok('tag', 'div'));
        this.lexer.defer(tok);
        return this.parseExpr();
      default:
        throw new Error('unexpected token "' + this.peek().type + '"');
    }
  },
  
  /**
   * Text
   */
  
  parseText: function(){
    var tok = this.expect('text')
      , node = new nodes.Text(tok.val);
    node.line = this.line();
    return node;
  },
  
  /**
   * code
   */
  
  parseCode: function(){
    var tok = this.expect('code')
      , node = new nodes.Code(tok.val, tok.buffer, tok.escape)
      , block
      , i = 1;
    node.line = this.line();
    while (this.lookahead(i) && 'newline' == this.lookahead(i).type) ++i;
    block = 'indent' == this.lookahead(i).type;
    if (block) {
      this.skip(i-1);
      node.block = this.block();
    }
    return node;
  },
  
  /**
   * comment
   */
  
  parseComment: function(){
    var tok = this.expect('comment')
      , node;

    if ('indent' == this.peek().type) {
      node = new nodes.BlockComment(tok.val, this.block(), tok.buffer);
    } else {
      node = new nodes.Comment(tok.val, tok.buffer);
    }

    node.line = this.line();
    return node;
  },
  
  /**
   * doctype
   */
  
  parseDoctype: function(){
    var tok = this.expect('doctype')
      , node = new nodes.Doctype(tok.val);
    node.line = this.line();
    return node;
  },
  
  /**
   * filter attrs? text-block
   */
  
  parseFilter: function(){
    var block
      , tok = this.expect('filter')
      , attrs = this.accept('attrs');

    this.lexer.pipeless = true;
    block = this.parseTextBlock();
    this.lexer.pipeless = false;

    var node = new nodes.Filter(tok.val, block, attrs && attrs.attrs);
    node.line = this.line();
    return node;
  },
  
  /**
   * tag ':' attrs? block
   */
  
  parseASTFilter: function(){
    var block
      , tok = this.expect('tag')
      , attrs = this.accept('attrs');

    this.expect(':');
    block = this.block();

    var node = new nodes.Filter(tok.val, block, attrs && attrs.attrs);
    node.line = this.line();
    return node;
  },
  
  /**
   * each block
   */
  
  parseEach: function(){
    var tok = this.expect('each')
      , node = new nodes.Each(tok.code, tok.val, tok.key, this.block());
    node.line = this.line();
    return node;
  },

  /**
   * 'extends' name
   */

  parseExtends: function(){
    var path = require('path')
      , fs = require('fs')
      , dirname = path.dirname
      , basename = path.basename
      , join = path.join;

    if (!this.filename)
      throw new Error('the "filename" option is required to extend templates');

    var path = name = this.expect('extends').val.trim()
      , dir = dirname(this.filename);

    var path = join(dir, path + '.jade')
      , str = fs.readFileSync(path, 'utf8')
      , parser = new Parser(str, path);

    parser.blocks = this.blocks;
    parser.contexts = this.contexts;
    this.extending = parser;

    // TODO: null node
    return new nodes.Literal('');
  },

  /**
   * 'block' name block
   */

  parseBlock: function(){
    var name = this.expect('block').val.trim()
      , block = this.block();
    return this.blocks[name] = this.blocks[name] || block;
  },

  /**
   * include
   */

  parseInclude: function(){
    var path = require('path')
      , fs = require('fs')
      , dirname = path.dirname
      , basename = path.basename
      , join = path.join;

    if (!this.filename)
      throw new Error('the "filename" option is required to use includes');

    var path = name = this.expect('include').val.trim()
      , dir = dirname(this.filename);

    // non-jade
    if (~basename(path).indexOf('.')) {
      var path = join(dir, path)
        , str = fs.readFileSync(path, 'utf8');
      return new nodes.Literal(str);
    }

    var path = join(dir, path + '.jade')
      , str = fs.readFileSync(path, 'utf8')
      , parser = new Parser(str, path);

    this.context(parser);
    var ast = parser.parse();
    this.context();
    ast.filename = path;
    return ast;
  },

  /**
   * mixin block
   */

  parseMixin: function(){
    var tok = this.expect('mixin')
      , name = tok.val
      , args = tok.args;
    var block = 'indent' == this.peek().type
      ? this.block()
      : null;
    return new nodes.Mixin(name, args, block);
  },

  /**
   * indent (text | newline)* outdent
   */

  parseTextBlock: function(){
    var text = new nodes.Text;
    text.line = this.line();
    var spaces = this.expect('indent').val;
    if (null == this._spaces) this._spaces = spaces;
    var indent = Array(spaces - this._spaces + 1).join(' ');
    while ('outdent' != this.peek().type) {
      switch (this.peek().type) {
        case 'newline':
          text.push('\\n');
          this.advance();
          break;
        case 'indent':
          text.push('\\n');
          this.parseTextBlock().nodes.forEach(function(node){
            text.push(node);
          });
          text.push('\\n');
          break;
        default:
          text.push(indent + this.advance().val);
      }
    }

    if (spaces == this._spaces) this._spaces = null;
    this.expect('outdent');
    return text;
  },

  /**
   * indent expr* outdent
   */
  
  block: function(){
    var block = new nodes.Block;
    block.line = this.line();
    this.expect('indent');
    while ('outdent' != this.peek().type) {
      if ('newline' == this.peek().type) {
        this.advance();
      } else {
        block.push(this.parseExpr());
      }
    }
    this.expect('outdent');
    return block;
  },

  /**
   * tag (attrs | class | id)* (text | code | ':')? newline* block?
   */
  
  parseTag: function(){
    // ast-filter look-ahead
    var i = 2;
    if ('attrs' == this.lookahead(i).type) ++i;
    if (':' == this.lookahead(i).type) {
      if ('indent' == this.lookahead(++i).type) {
        return this.parseASTFilter();
      }
    }

    var name = this.advance().val
      , tag = new nodes.Tag(name)
      , dot;

    tag.line = this.line();

    // (attrs | class | id)*
    out:
      while (true) {
        switch (this.peek().type) {
          case 'id':
          case 'class':
            var tok = this.advance();
            tag.setAttribute(tok.type, "'" + tok.val + "'");
            continue;
          case 'attrs':
            var obj = this.advance().attrs
              , names = Object.keys(obj);
            for (var i = 0, len = names.length; i < len; ++i) {
              var name = names[i]
                , val = obj[name];
              tag.setAttribute(name, val);
            }
            continue;
          default:
            break out;
        }
      }

    // check immediate '.'
    if ('.' == this.peek().val) {
      dot = tag.textOnly = true;
      this.advance();
    }

    // (text | code | ':')?
    switch (this.peek().type) {
      case 'text':
        tag.text = this.parseText();
        break;
      case 'code':
        tag.code = this.parseCode();
        break;
      case ':':
        this.advance();
        tag.block = new nodes.Block;
        tag.block.push(this.parseTag());
        break;
    }

    // newline*
    while ('newline' == this.peek().type) this.advance();

    tag.textOnly = tag.textOnly || ~textOnly.indexOf(tag.name);

    // script special-case
    if ('script' == tag.name) {
      var type = tag.getAttribute('type');
      if (!dot && type && 'text/javascript' != type.replace(/^['"]|['"]$/g, '')) {
        tag.textOnly = false;
      }
    }

    // block?
    if ('indent' == this.peek().type) {
      if (tag.textOnly) {
        this.lexer.pipeless = true;
        tag.block = this.parseTextBlock();
        this.lexer.pipeless = false;
      } else {
        var block = this.block();
        if (tag.block) {
          for (var i = 0, len = block.nodes.length; i < len; ++i) {
            tag.block.push(block.nodes[i]);
          }
        } else {
          tag.block = block;
        }
      }
    }
    
    return tag;
  }
};

}); // module: parser.js

require.register("runtime.js", function(module, exports, require){

/*!
 * Jade - runtime
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Lame Array.isArray() polyfill for now.
 */

if (!Array.isArray) {
  Array.isArray = function(arr){
    return '[object Array]' == toString.call(arr);
  };
}

/**
 * Lame Object.keys() polyfill for now.
 */

if (!Object.keys) {
  Object.keys = function(obj){
    var arr = [];
    for (var key in obj) {
      if (obj.hasOwnProperty(key)) {
        arr.push(key);
      }
    }
    return arr;
  } 
}

/**
 * Render the given attributes object.
 *
 * @param {Object} obj
 * @return {String}
 * @api private
 */

exports.attrs = function attrs(obj){
  var buf = []
    , terse = obj.terse;
  delete obj.terse;
  var keys = Object.keys(obj)
    , len = keys.length;
  if (len) {
    buf.push('');
    for (var i = 0; i < len; ++i) {
      var key = keys[i]
        , val = obj[key];
      if ('boolean' == typeof val || null == val) {
        if (val) {
          terse
            ? buf.push(key)
            : buf.push(key + '="' + key + '"');
        }
      } else if ('class' == key && Array.isArray(val)) {
        buf.push(key + '="' + exports.escape(val.join(' ')) + '"');
      } else {
        buf.push(key + '="' + exports.escape(val) + '"');
      }
    }
  }
  return buf.join(' ');
};

/**
 * Escape the given string of `html`.
 *
 * @param {String} html
 * @return {String}
 * @api private
 */

exports.escape = function escape(html){
  if( html==null )
    return "";
  return String(html)
    .replace(/&(?!\w+;)/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
};

exports.string = function string(html){
  if( html==null )
    return "";
  return String(html);
};

exports.templates = {}
  
exports.init = function init(){
  return {
    string: exports.string,
    escape: exports.escape,
    attrs: exports.attrs,
    rethrow: exports.rethrow,
    buf: []
  }
}

/**
 * Re-throw the given `err` in context to the
 * the jade in `filename` at the given `lineno`.
 *
 * @param {Error} err
 * @param {String} filename
 * @param {String} lineno
 * @api private
 */

exports.rethrow = function rethrow(err, filename, lineno) {
  if( !filename )
    throw err;
    
  var context = 3
    , str = require('fs').readFileSync(filename, 'utf8')
    , lines = str.split('\n')
    , start = Math.max(lineno - context, 0)
    , end = Math.min(lines.length, lineno + context); 

  // Error context
  var context = lines.slice(start, end).map(function(line, i){
    var curr = i + start + 1;
    return (curr == lineno ? '  > ' : '    ')
      + curr
      + '| '
      + line;
  }).join('\n');

  // Alter exception message
  err.path = filename;
  err.message = (filename || 'Jade') + ':' + lineno 
    + '\n' + context + '\n\n' + err.message;
  throw err;
};

}); // module: runtime.js

require.register("self-closing.js", function(module, exports, require){

/*!
 * Jade - self closing tags
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

module.exports = [
    'meta'
  , 'img'
  , 'link'
  , 'input'
  , 'area'
  , 'base'
  , 'col'
  , 'br'
  , 'hr'
];
}); // module: self-closing.js

require.register("utils.js", function(module, exports, require){

/*!
 * Jade - utils
 * Copyright(c) 2010 TJ Holowaychuk <tj@vision-media.ca>
 * MIT Licensed
 */

/**
 * Convert interpolation in the given string to JavaScript.
 *
 * @param {String} str
 * @return {String}
 * @api private
 */

var interpolate = exports.interpolate = function(str){
  return str.replace(/(\\)?([#!]){(.*?)}/g, function(str, escape, flag, code){
    return escape
      ? str
      : "' + "
        + ('!' == flag ? '__.string' : '__.escape')
        + "("+code.replace(/\\'/g, "'")+") + '";
        // + "((interp = " + 
        // + ") == null ? '' : interp) + '";
  });
};

/**
 * Escape single quotes in `str`.
 *
 * @param {String} str
 * @return {String}
 * @api private
 */

var escape = exports.escape = function(str) {
  return str.replace(/'/g, "\\'");
};

/**
 * Interpolate, and escape the given `str`.
 *
 * @param {String} str
 * @return {String}
 * @api private
 */

exports.text = function(str){
  return interpolate(escape(str));
};
}); // module: utils.js

window.jade = require("jade.js");
})(this);
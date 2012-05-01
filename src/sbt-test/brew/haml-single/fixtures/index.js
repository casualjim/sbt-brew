(function(define){
define(function(){return function(vars){
with(vars||{}) {
return "<div id=\"someotherid\"><div id=\"hello\">asd</div></div>"; 
}};
});})(typeof define=="function"?
define:
function(factory){module.exports=factory.apply(this, deps.map(require));});
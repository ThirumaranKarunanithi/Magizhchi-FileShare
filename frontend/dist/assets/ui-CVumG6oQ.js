import{r as m}from"./react-Cp1bHmlf.js";let ve={data:""},pe=e=>{if(typeof window=="object"){let t=(e?e.querySelector("#_goober"):window._goober)||Object.assign(document.createElement("style"),{innerHTML:" ",id:"_goober"});return t.nonce=window.__nonce__,t.parentNode||(e||document.head).appendChild(t),t.firstChild}return e||ve},xe=/(?:([\u0080-\uFFFF\w-%@]+) *:? *([^{;]+?);|([^;}{]*?) *{)|(}\s*)/g,Me=/\/\*[^]*?\*\/|  +/g,K=/\n+/g,k=(e,t)=>{let r="",n="",a="";for(let s in e){let i=e[s];s[0]=="@"?s[1]=="i"?r=s+" "+i+";":n+=s[1]=="f"?k(i,s):s+"{"+k(i,s[1]=="k"?"":t)+"}":typeof i=="object"?n+=k(i,t?t.replace(/([^,])+/g,o=>s.replace(/([^,]*:\S+\([^)]*\))|([^,])+/g,c=>/&/.test(c)?c.replace(/&/g,o):o?o+" "+c:c)):s):i!=null&&(s=/^--/.test(s)?s:s.replace(/[A-Z]/g,"-$&").toLowerCase(),a+=k.p?k.p(s,i):s+":"+i+";")}return r+(t&&a?t+"{"+a+"}":a)+n},M={},se=e=>{if(typeof e=="object"){let t="";for(let r in e)t+=r+se(e[r]);return t}return e},De=(e,t,r,n,a)=>{let s=se(e),i=M[s]||(M[s]=(c=>{let u=0,d=11;for(;u<c.length;)d=101*d+c.charCodeAt(u++)>>>0;return"go"+d})(s));if(!M[i]){let c=s!==e?e:(u=>{let d,f,l=[{}];for(;d=xe.exec(u.replace(Me,""));)d[4]?l.shift():d[3]?(f=d[3].replace(K," ").trim(),l.unshift(l[0][f]=l[0][f]||{})):l[0][d[1]]=d[2].replace(K," ").trim();return l[0]})(e);M[i]=k(a?{["@keyframes "+i]:c}:c,r?"":"."+i)}let o=r&&M.g?M.g:null;return r&&(M.g=M[i]),((c,u,d,f)=>{f?u.data=u.data.replace(f,c):u.data.indexOf(c)===-1&&(u.data=d?c+u.data:u.data+c)})(M[i],t,n,o),i},Oe=(e,t,r)=>e.reduce((n,a,s)=>{let i=t[s];if(i&&i.call){let o=i(r),c=o&&o.props&&o.props.className||/^go/.test(o)&&o;i=c?"."+c:o&&typeof o=="object"?o.props?"":k(o,""):o===!1?"":o}return n+a+(i??"")},"");function L(e){let t=this||{},r=e.call?e(t.p):e;return De(r.unshift?r.raw?Oe(r,[].slice.call(arguments,1),t.p):r.reduce((n,a)=>Object.assign(n,a&&a.call?a(t.p):a),{}):r,pe(t.target),t.g,t.o,t.k)}let oe,G,V;L.bind({g:1});let D=L.bind({k:1});function Pe(e,t,r,n){k.p=t,oe=e,G=r,V=n}function S(e,t){let r=this||{};return function(){let n=arguments;function a(s,i){let o=Object.assign({},s),c=o.className||a.className;r.p=Object.assign({theme:G&&G()},o),r.o=/ *go\d+/.test(c),o.className=L.apply(r,n)+(c?" "+c:"");let u=e;return e[0]&&(u=o.as||e,delete o.as),V&&u[0]&&V(o),oe(u,o)}return a}}var ke=e=>typeof e=="function",X=(e,t)=>ke(e)?e(t):e,Se=(()=>{let e=0;return()=>(++e).toString()})(),ce=(()=>{let e;return()=>{if(e===void 0&&typeof window<"u"){let t=matchMedia("(prefers-reduced-motion: reduce)");e=!t||t.matches}return e}})(),We=20,J="default",ue=(e,t)=>{let{toastLimit:r}=e.settings;switch(t.type){case 0:return{...e,toasts:[t.toast,...e.toasts].slice(0,r)};case 1:return{...e,toasts:e.toasts.map(i=>i.id===t.toast.id?{...i,...t.toast}:i)};case 2:let{toast:n}=t;return ue(e,{type:e.toasts.find(i=>i.id===n.id)?1:0,toast:n});case 3:let{toastId:a}=t;return{...e,toasts:e.toasts.map(i=>i.id===a||a===void 0?{...i,dismissed:!0,visible:!1}:i)};case 4:return t.toastId===void 0?{...e,toasts:[]}:{...e,toasts:e.toasts.filter(i=>i.id!==t.toastId)};case 5:return{...e,pausedAt:t.time};case 6:let s=t.time-(e.pausedAt||0);return{...e,pausedAt:void 0,toasts:e.toasts.map(i=>({...i,pauseDuration:i.pauseDuration+s}))}}},j=[],de={toasts:[],pausedAt:void 0,settings:{toastLimit:We}},x={},le=(e,t=J)=>{x[t]=ue(x[t]||de,e),j.forEach(([r,n])=>{r===t&&n(x[t])})},fe=e=>Object.keys(x).forEach(t=>le(e,t)),Ee=e=>Object.keys(x).find(t=>x[t].toasts.some(r=>r.id===e)),Q=(e=J)=>t=>{le(t,e)},Te={blank:4e3,error:4e3,success:2e3,loading:1/0,custom:4e3},Ye=(e={},t=J)=>{let[r,n]=m.useState(x[t]||de),a=m.useRef(x[t]);m.useEffect(()=>(a.current!==x[t]&&n(x[t]),j.push([t,n]),()=>{let i=j.findIndex(([o])=>o===t);i>-1&&j.splice(i,1)}),[t]);let s=r.toasts.map(i=>{var o,c,u;return{...e,...e[i.type],...i,removeDelay:i.removeDelay||((o=e[i.type])==null?void 0:o.removeDelay)||(e==null?void 0:e.removeDelay),duration:i.duration||((c=e[i.type])==null?void 0:c.duration)||(e==null?void 0:e.duration)||Te[i.type],style:{...e.style,...(u=e[i.type])==null?void 0:u.style,...i.style}}});return{...r,toasts:s}},Fe=(e,t="blank",r)=>({createdAt:Date.now(),visible:!0,dismissed:!1,type:t,ariaProps:{role:"status","aria-live":"polite"},message:e,pauseDuration:0,...r,id:(r==null?void 0:r.id)||Se()}),N=e=>(t,r)=>{let n=Fe(t,e,r);return Q(n.toasterId||Ee(n.id))({type:2,toast:n}),n.id},w=(e,t)=>N("blank")(e,t);w.error=N("error");w.success=N("success");w.loading=N("loading");w.custom=N("custom");w.dismiss=(e,t)=>{let r={type:3,toastId:e};t?Q(t)(r):fe(r)};w.dismissAll=e=>w.dismiss(void 0,e);w.remove=(e,t)=>{let r={type:4,toastId:e};t?Q(t)(r):fe(r)};w.removeAll=e=>w.remove(void 0,e);w.promise=(e,t,r)=>{let n=w.loading(t.loading,{...r,...r==null?void 0:r.loading});return typeof e=="function"&&(e=e()),e.then(a=>{let s=t.success?X(t.success,a):void 0;return s?w.success(s,{id:n,...r,...r==null?void 0:r.success}):w.dismiss(n),a}).catch(a=>{let s=t.error?X(t.error,a):void 0;s?w.error(s,{id:n,...r,...r==null?void 0:r.error}):w.dismiss(n)}),e};var Ce=1e3,Ne=(e,t="default")=>{let{toasts:r,pausedAt:n}=Ye(e,t),a=m.useRef(new Map).current,s=m.useCallback((f,l=Ce)=>{if(a.has(f))return;let h=setTimeout(()=>{a.delete(f),i({type:4,toastId:f})},l);a.set(f,h)},[]);m.useEffect(()=>{if(n)return;let f=Date.now(),l=r.map(h=>{if(h.duration===1/0)return;let y=(h.duration||0)+h.pauseDuration-(f-h.createdAt);if(y<0){h.visible&&w.dismiss(h.id);return}return setTimeout(()=>w.dismiss(h.id,t),y)});return()=>{l.forEach(h=>h&&clearTimeout(h))}},[r,n,t]);let i=m.useCallback(Q(t),[t]),o=m.useCallback(()=>{i({type:5,time:Date.now()})},[i]),c=m.useCallback((f,l)=>{i({type:1,toast:{id:f,height:l}})},[i]),u=m.useCallback(()=>{n&&i({type:6,time:Date.now()})},[n,i]),d=m.useCallback((f,l)=>{let{reverseOrder:h=!1,gutter:y=8,defaultPosition:b}=l||{},W=r.filter(p=>(p.position||b)===(f.position||b)&&p.height),we=W.findIndex(p=>p.id===f.id),U=W.filter((p,B)=>B<we&&p.visible).length;return W.filter(p=>p.visible).slice(...h?[U+1]:[0,U]).reduce((p,B)=>p+(B.height||0)+y,0)},[r]);return m.useEffect(()=>{r.forEach(f=>{if(f.dismissed)s(f.id,f.removeDelay);else{let l=a.get(f.id);l&&(clearTimeout(l),a.delete(f.id))}})},[r,s]),{toasts:r,handlers:{updateHeight:c,startPause:o,endPause:u,calculateOffset:d}}},_e=D`
from {
  transform: scale(0) rotate(45deg);
	opacity: 0;
}
to {
 transform: scale(1) rotate(45deg);
  opacity: 1;
}`,qe=D`
from {
  transform: scale(0);
  opacity: 0;
}
to {
  transform: scale(1);
  opacity: 1;
}`,He=D`
from {
  transform: scale(0) rotate(90deg);
	opacity: 0;
}
to {
  transform: scale(1) rotate(90deg);
	opacity: 1;
}`,je=S("div")`
  width: 20px;
  opacity: 0;
  height: 20px;
  border-radius: 10px;
  background: ${e=>e.primary||"#ff4b4b"};
  position: relative;
  transform: rotate(45deg);

  animation: ${_e} 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275)
    forwards;
  animation-delay: 100ms;

  &:after,
  &:before {
    content: '';
    animation: ${qe} 0.15s ease-out forwards;
    animation-delay: 150ms;
    position: absolute;
    border-radius: 3px;
    opacity: 0;
    background: ${e=>e.secondary||"#fff"};
    bottom: 9px;
    left: 4px;
    height: 2px;
    width: 12px;
  }

  &:before {
    animation: ${He} 0.15s ease-out forwards;
    animation-delay: 180ms;
    transform: rotate(90deg);
  }
`,Ie=D`
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
`,Xe=S("div")`
  width: 12px;
  height: 12px;
  box-sizing: border-box;
  border: 2px solid;
  border-radius: 100%;
  border-color: ${e=>e.secondary||"#e0e0e0"};
  border-right-color: ${e=>e.primary||"#616161"};
  animation: ${Ie} 1s linear infinite;
`,$e=D`
from {
  transform: scale(0) rotate(45deg);
	opacity: 0;
}
to {
  transform: scale(1) rotate(45deg);
	opacity: 1;
}`,Ae=D`
0% {
	height: 0;
	width: 0;
	opacity: 0;
}
40% {
  height: 0;
	width: 6px;
	opacity: 1;
}
100% {
  opacity: 1;
  height: 10px;
}`,Le=S("div")`
  width: 20px;
  opacity: 0;
  height: 20px;
  border-radius: 10px;
  background: ${e=>e.primary||"#61d345"};
  position: relative;
  transform: rotate(45deg);

  animation: ${$e} 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275)
    forwards;
  animation-delay: 100ms;
  &:after {
    content: '';
    box-sizing: border-box;
    animation: ${Ae} 0.2s ease-out forwards;
    opacity: 0;
    animation-delay: 200ms;
    position: absolute;
    border-right: 2px solid;
    border-bottom: 2px solid;
    border-color: ${e=>e.secondary||"#fff"};
    bottom: 6px;
    left: 6px;
    height: 10px;
    width: 6px;
  }
`,Qe=S("div")`
  position: absolute;
`,Re=S("div")`
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-width: 20px;
  min-height: 20px;
`,Be=D`
from {
  transform: scale(0.6);
  opacity: 0.4;
}
to {
  transform: scale(1);
  opacity: 1;
}`,ze=S("div")`
  position: relative;
  transform: scale(0.6);
  opacity: 0.4;
  min-width: 20px;
  animation: ${Be} 0.3s 0.12s cubic-bezier(0.175, 0.885, 0.32, 1.275)
    forwards;
`,Ge=({toast:e})=>{let{icon:t,type:r,iconTheme:n}=e;return t!==void 0?typeof t=="string"?m.createElement(ze,null,t):t:r==="blank"?null:m.createElement(Re,null,m.createElement(Xe,{...n}),r!=="loading"&&m.createElement(Qe,null,r==="error"?m.createElement(je,{...n}):m.createElement(Le,{...n})))},Ve=e=>`
0% {transform: translate3d(0,${e*-200}%,0) scale(.6); opacity:.5;}
100% {transform: translate3d(0,0,0) scale(1); opacity:1;}
`,Je=e=>`
0% {transform: translate3d(0,0,-1px) scale(1); opacity:1;}
100% {transform: translate3d(0,${e*-150}%,-1px) scale(.6); opacity:0;}
`,Ue="0%{opacity:0;} 100%{opacity:1;}",Ke="0%{opacity:1;} 100%{opacity:0;}",Ze=S("div")`
  display: flex;
  align-items: center;
  background: #fff;
  color: #363636;
  line-height: 1.3;
  will-change: transform;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.1), 0 3px 3px rgba(0, 0, 0, 0.05);
  max-width: 350px;
  pointer-events: auto;
  padding: 8px 10px;
  border-radius: 8px;
`,et=S("div")`
  display: flex;
  justify-content: center;
  margin: 4px 10px;
  color: inherit;
  flex: 1 1 auto;
  white-space: pre-line;
`,tt=(e,t)=>{let r=e.includes("top")?1:-1,[n,a]=ce()?[Ue,Ke]:[Ve(r),Je(r)];return{animation:t?`${D(n)} 0.35s cubic-bezier(.21,1.02,.73,1) forwards`:`${D(a)} 0.4s forwards cubic-bezier(.06,.71,.55,1)`}},rt=m.memo(({toast:e,position:t,style:r,children:n})=>{let a=e.height?tt(e.position||t||"top-center",e.visible):{opacity:0},s=m.createElement(Ge,{toast:e}),i=m.createElement(et,{...e.ariaProps},X(e.message,e));return m.createElement(Ze,{className:e.className,style:{...a,...r,...e.style}},typeof n=="function"?n({icon:s,message:i}):m.createElement(m.Fragment,null,s,i))});Pe(m.createElement);var nt=({id:e,className:t,style:r,onHeightUpdate:n,children:a})=>{let s=m.useCallback(i=>{if(i){let o=()=>{let c=i.getBoundingClientRect().height;n(e,c)};o(),new MutationObserver(o).observe(i,{subtree:!0,childList:!0,characterData:!0})}},[e,n]);return m.createElement("div",{ref:s,className:t,style:r},a)},at=(e,t)=>{let r=e.includes("top"),n=r?{top:0}:{bottom:0},a=e.includes("center")?{justifyContent:"center"}:e.includes("right")?{justifyContent:"flex-end"}:{};return{left:0,right:0,display:"flex",position:"absolute",transition:ce()?void 0:"all 230ms cubic-bezier(.21,1.02,.73,1)",transform:`translateY(${t*(r?1:-1)}px)`,...n,...a}},it=L`
  z-index: 9999;
  > * {
    pointer-events: auto;
  }
`,q=16,xr=({reverseOrder:e,position:t="top-center",toastOptions:r,gutter:n,children:a,toasterId:s,containerStyle:i,containerClassName:o})=>{let{toasts:c,handlers:u}=Ne(r,s);return m.createElement("div",{"data-rht-toaster":s||"",style:{position:"fixed",zIndex:9999,top:q,left:q,right:q,bottom:q,pointerEvents:"none",...i},className:o,onMouseEnter:u.startPause,onMouseLeave:u.endPause},c.map(d=>{let f=d.position||t,l=u.calculateOffset(d,{reverseOrder:e,gutter:n,defaultPosition:t}),h=at(f,l);return m.createElement(nt,{id:d.id,key:d.id,onHeightUpdate:u.updateHeight,className:d.visible?it:"",style:h},d.type==="custom"?X(d.message,d):a?a(d):m.createElement(rt,{toast:d,position:f}))}))},Mr=w;const me=6048e5,st=864e5,H=43200,Z=1440,ee=Symbol.for("constructDateFrom");function O(e,t){return typeof e=="function"?e(t):e&&typeof e=="object"&&ee in e?e[ee](t):e instanceof Date?new e.constructor(t):new Date(t)}function v(e,t){return O(t||e,e)}let ot={};function _(){return ot}function C(e,t){var o,c,u,d;const r=_(),n=(t==null?void 0:t.weekStartsOn)??((c=(o=t==null?void 0:t.locale)==null?void 0:o.options)==null?void 0:c.weekStartsOn)??r.weekStartsOn??((d=(u=r.locale)==null?void 0:u.options)==null?void 0:d.weekStartsOn)??0,a=v(e,t==null?void 0:t.in),s=a.getDay(),i=(s<n?7:0)+s-n;return a.setDate(a.getDate()-i),a.setHours(0,0,0,0),a}function $(e,t){return C(e,{...t,weekStartsOn:1})}function he(e,t){const r=v(e,t==null?void 0:t.in),n=r.getFullYear(),a=O(r,0);a.setFullYear(n+1,0,4),a.setHours(0,0,0,0);const s=$(a),i=O(r,0);i.setFullYear(n,0,4),i.setHours(0,0,0,0);const o=$(i);return r.getTime()>=s.getTime()?n+1:r.getTime()>=o.getTime()?n:n-1}function A(e){const t=v(e),r=new Date(Date.UTC(t.getFullYear(),t.getMonth(),t.getDate(),t.getHours(),t.getMinutes(),t.getSeconds(),t.getMilliseconds()));return r.setUTCFullYear(t.getFullYear()),+e-+r}function R(e,...t){const r=O.bind(null,e||t.find(n=>typeof n=="object"));return t.map(r)}function te(e,t){const r=v(e,t==null?void 0:t.in);return r.setHours(0,0,0,0),r}function ct(e,t,r){const[n,a]=R(r==null?void 0:r.in,e,t),s=te(n),i=te(a),o=+s-A(s),c=+i-A(i);return Math.round((o-c)/st)}function ut(e,t){const r=he(e,t),n=O(e,0);return n.setFullYear(r,0,4),n.setHours(0,0,0,0),$(n)}function I(e,t){const r=+v(e)-+v(t);return r<0?-1:r>0?1:r}function dt(e){return O(e,Date.now())}function lt(e){return e instanceof Date||typeof e=="object"&&Object.prototype.toString.call(e)==="[object Date]"}function ft(e){return!(!lt(e)&&typeof e!="number"||isNaN(+v(e)))}function mt(e,t,r){const[n,a]=R(r==null?void 0:r.in,e,t),s=n.getFullYear()-a.getFullYear(),i=n.getMonth()-a.getMonth();return s*12+i}function ht(e){return t=>{const n=(e?Math[e]:Math.trunc)(t);return n===0?0:n}}function gt(e,t){return+v(e)-+v(t)}function yt(e,t){const r=v(e,t==null?void 0:t.in);return r.setHours(23,59,59,999),r}function bt(e,t){const r=v(e,t==null?void 0:t.in),n=r.getMonth();return r.setFullYear(r.getFullYear(),n+1,0),r.setHours(23,59,59,999),r}function wt(e,t){const r=v(e,t==null?void 0:t.in);return+yt(r,t)==+bt(r,t)}function vt(e,t,r){const[n,a,s]=R(r==null?void 0:r.in,e,e,t),i=I(a,s),o=Math.abs(mt(a,s));if(o<1)return 0;a.getMonth()===1&&a.getDate()>27&&a.setDate(30),a.setMonth(a.getMonth()-i*o);let c=I(a,s)===-i;wt(n)&&o===1&&I(n,s)===1&&(c=!1);const u=i*(o-+c);return u===0?0:u}function pt(e,t,r){const n=gt(e,t)/1e3;return ht(r==null?void 0:r.roundingMethod)(n)}function xt(e,t){const r=v(e,t==null?void 0:t.in);return r.setFullYear(r.getFullYear(),0,1),r.setHours(0,0,0,0),r}const Mt={lessThanXSeconds:{one:"less than a second",other:"less than {{count}} seconds"},xSeconds:{one:"1 second",other:"{{count}} seconds"},halfAMinute:"half a minute",lessThanXMinutes:{one:"less than a minute",other:"less than {{count}} minutes"},xMinutes:{one:"1 minute",other:"{{count}} minutes"},aboutXHours:{one:"about 1 hour",other:"about {{count}} hours"},xHours:{one:"1 hour",other:"{{count}} hours"},xDays:{one:"1 day",other:"{{count}} days"},aboutXWeeks:{one:"about 1 week",other:"about {{count}} weeks"},xWeeks:{one:"1 week",other:"{{count}} weeks"},aboutXMonths:{one:"about 1 month",other:"about {{count}} months"},xMonths:{one:"1 month",other:"{{count}} months"},aboutXYears:{one:"about 1 year",other:"about {{count}} years"},xYears:{one:"1 year",other:"{{count}} years"},overXYears:{one:"over 1 year",other:"over {{count}} years"},almostXYears:{one:"almost 1 year",other:"almost {{count}} years"}},Dt=(e,t,r)=>{let n;const a=Mt[e];return typeof a=="string"?n=a:t===1?n=a.one:n=a.other.replace("{{count}}",t.toString()),r!=null&&r.addSuffix?r.comparison&&r.comparison>0?"in "+n:n+" ago":n};function z(e){return(t={})=>{const r=t.width?String(t.width):e.defaultWidth;return e.formats[r]||e.formats[e.defaultWidth]}}const Ot={full:"EEEE, MMMM do, y",long:"MMMM do, y",medium:"MMM d, y",short:"MM/dd/yyyy"},Pt={full:"h:mm:ss a zzzz",long:"h:mm:ss a z",medium:"h:mm:ss a",short:"h:mm a"},kt={full:"{{date}} 'at' {{time}}",long:"{{date}} 'at' {{time}}",medium:"{{date}}, {{time}}",short:"{{date}}, {{time}}"},St={date:z({formats:Ot,defaultWidth:"full"}),time:z({formats:Pt,defaultWidth:"full"}),dateTime:z({formats:kt,defaultWidth:"full"})},Wt={lastWeek:"'last' eeee 'at' p",yesterday:"'yesterday at' p",today:"'today at' p",tomorrow:"'tomorrow at' p",nextWeek:"eeee 'at' p",other:"P"},Et=(e,t,r,n)=>Wt[e];function Y(e){return(t,r)=>{const n=r!=null&&r.context?String(r.context):"standalone";let a;if(n==="formatting"&&e.formattingValues){const i=e.defaultFormattingWidth||e.defaultWidth,o=r!=null&&r.width?String(r.width):i;a=e.formattingValues[o]||e.formattingValues[i]}else{const i=e.defaultWidth,o=r!=null&&r.width?String(r.width):e.defaultWidth;a=e.values[o]||e.values[i]}const s=e.argumentCallback?e.argumentCallback(t):t;return a[s]}}const Tt={narrow:["B","A"],abbreviated:["BC","AD"],wide:["Before Christ","Anno Domini"]},Yt={narrow:["1","2","3","4"],abbreviated:["Q1","Q2","Q3","Q4"],wide:["1st quarter","2nd quarter","3rd quarter","4th quarter"]},Ft={narrow:["J","F","M","A","M","J","J","A","S","O","N","D"],abbreviated:["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"],wide:["January","February","March","April","May","June","July","August","September","October","November","December"]},Ct={narrow:["S","M","T","W","T","F","S"],short:["Su","Mo","Tu","We","Th","Fr","Sa"],abbreviated:["Sun","Mon","Tue","Wed","Thu","Fri","Sat"],wide:["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"]},Nt={narrow:{am:"a",pm:"p",midnight:"mi",noon:"n",morning:"morning",afternoon:"afternoon",evening:"evening",night:"night"},abbreviated:{am:"AM",pm:"PM",midnight:"midnight",noon:"noon",morning:"morning",afternoon:"afternoon",evening:"evening",night:"night"},wide:{am:"a.m.",pm:"p.m.",midnight:"midnight",noon:"noon",morning:"morning",afternoon:"afternoon",evening:"evening",night:"night"}},_t={narrow:{am:"a",pm:"p",midnight:"mi",noon:"n",morning:"in the morning",afternoon:"in the afternoon",evening:"in the evening",night:"at night"},abbreviated:{am:"AM",pm:"PM",midnight:"midnight",noon:"noon",morning:"in the morning",afternoon:"in the afternoon",evening:"in the evening",night:"at night"},wide:{am:"a.m.",pm:"p.m.",midnight:"midnight",noon:"noon",morning:"in the morning",afternoon:"in the afternoon",evening:"in the evening",night:"at night"}},qt=(e,t)=>{const r=Number(e),n=r%100;if(n>20||n<10)switch(n%10){case 1:return r+"st";case 2:return r+"nd";case 3:return r+"rd"}return r+"th"},Ht={ordinalNumber:qt,era:Y({values:Tt,defaultWidth:"wide"}),quarter:Y({values:Yt,defaultWidth:"wide",argumentCallback:e=>e-1}),month:Y({values:Ft,defaultWidth:"wide"}),day:Y({values:Ct,defaultWidth:"wide"}),dayPeriod:Y({values:Nt,defaultWidth:"wide",formattingValues:_t,defaultFormattingWidth:"wide"})};function F(e){return(t,r={})=>{const n=r.width,a=n&&e.matchPatterns[n]||e.matchPatterns[e.defaultMatchWidth],s=t.match(a);if(!s)return null;const i=s[0],o=n&&e.parsePatterns[n]||e.parsePatterns[e.defaultParseWidth],c=Array.isArray(o)?It(o,f=>f.test(i)):jt(o,f=>f.test(i));let u;u=e.valueCallback?e.valueCallback(c):c,u=r.valueCallback?r.valueCallback(u):u;const d=t.slice(i.length);return{value:u,rest:d}}}function jt(e,t){for(const r in e)if(Object.prototype.hasOwnProperty.call(e,r)&&t(e[r]))return r}function It(e,t){for(let r=0;r<e.length;r++)if(t(e[r]))return r}function Xt(e){return(t,r={})=>{const n=t.match(e.matchPattern);if(!n)return null;const a=n[0],s=t.match(e.parsePattern);if(!s)return null;let i=e.valueCallback?e.valueCallback(s[0]):s[0];i=r.valueCallback?r.valueCallback(i):i;const o=t.slice(a.length);return{value:i,rest:o}}}const $t=/^(\d+)(th|st|nd|rd)?/i,At=/\d+/i,Lt={narrow:/^(b|a)/i,abbreviated:/^(b\.?\s?c\.?|b\.?\s?c\.?\s?e\.?|a\.?\s?d\.?|c\.?\s?e\.?)/i,wide:/^(before christ|before common era|anno domini|common era)/i},Qt={any:[/^b/i,/^(a|c)/i]},Rt={narrow:/^[1234]/i,abbreviated:/^q[1234]/i,wide:/^[1234](th|st|nd|rd)? quarter/i},Bt={any:[/1/i,/2/i,/3/i,/4/i]},zt={narrow:/^[jfmasond]/i,abbreviated:/^(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)/i,wide:/^(january|february|march|april|may|june|july|august|september|october|november|december)/i},Gt={narrow:[/^j/i,/^f/i,/^m/i,/^a/i,/^m/i,/^j/i,/^j/i,/^a/i,/^s/i,/^o/i,/^n/i,/^d/i],any:[/^ja/i,/^f/i,/^mar/i,/^ap/i,/^may/i,/^jun/i,/^jul/i,/^au/i,/^s/i,/^o/i,/^n/i,/^d/i]},Vt={narrow:/^[smtwf]/i,short:/^(su|mo|tu|we|th|fr|sa)/i,abbreviated:/^(sun|mon|tue|wed|thu|fri|sat)/i,wide:/^(sunday|monday|tuesday|wednesday|thursday|friday|saturday)/i},Jt={narrow:[/^s/i,/^m/i,/^t/i,/^w/i,/^t/i,/^f/i,/^s/i],any:[/^su/i,/^m/i,/^tu/i,/^w/i,/^th/i,/^f/i,/^sa/i]},Ut={narrow:/^(a|p|mi|n|(in the|at) (morning|afternoon|evening|night))/i,any:/^([ap]\.?\s?m\.?|midnight|noon|(in the|at) (morning|afternoon|evening|night))/i},Kt={any:{am:/^a/i,pm:/^p/i,midnight:/^mi/i,noon:/^no/i,morning:/morning/i,afternoon:/afternoon/i,evening:/evening/i,night:/night/i}},Zt={ordinalNumber:Xt({matchPattern:$t,parsePattern:At,valueCallback:e=>parseInt(e,10)}),era:F({matchPatterns:Lt,defaultMatchWidth:"wide",parsePatterns:Qt,defaultParseWidth:"any"}),quarter:F({matchPatterns:Rt,defaultMatchWidth:"wide",parsePatterns:Bt,defaultParseWidth:"any",valueCallback:e=>e+1}),month:F({matchPatterns:zt,defaultMatchWidth:"wide",parsePatterns:Gt,defaultParseWidth:"any"}),day:F({matchPatterns:Vt,defaultMatchWidth:"wide",parsePatterns:Jt,defaultParseWidth:"any"}),dayPeriod:F({matchPatterns:Ut,defaultMatchWidth:"any",parsePatterns:Kt,defaultParseWidth:"any"})},ge={code:"en-US",formatDistance:Dt,formatLong:St,formatRelative:Et,localize:Ht,match:Zt,options:{weekStartsOn:0,firstWeekContainsDate:1}};function er(e,t){const r=v(e,t==null?void 0:t.in);return ct(r,xt(r))+1}function tr(e,t){const r=v(e,t==null?void 0:t.in),n=+$(r)-+ut(r);return Math.round(n/me)+1}function ye(e,t){var d,f,l,h;const r=v(e,t==null?void 0:t.in),n=r.getFullYear(),a=_(),s=(t==null?void 0:t.firstWeekContainsDate)??((f=(d=t==null?void 0:t.locale)==null?void 0:d.options)==null?void 0:f.firstWeekContainsDate)??a.firstWeekContainsDate??((h=(l=a.locale)==null?void 0:l.options)==null?void 0:h.firstWeekContainsDate)??1,i=O((t==null?void 0:t.in)||e,0);i.setFullYear(n+1,0,s),i.setHours(0,0,0,0);const o=C(i,t),c=O((t==null?void 0:t.in)||e,0);c.setFullYear(n,0,s),c.setHours(0,0,0,0);const u=C(c,t);return+r>=+o?n+1:+r>=+u?n:n-1}function rr(e,t){var o,c,u,d;const r=_(),n=(t==null?void 0:t.firstWeekContainsDate)??((c=(o=t==null?void 0:t.locale)==null?void 0:o.options)==null?void 0:c.firstWeekContainsDate)??r.firstWeekContainsDate??((d=(u=r.locale)==null?void 0:u.options)==null?void 0:d.firstWeekContainsDate)??1,a=ye(e,t),s=O((t==null?void 0:t.in)||e,0);return s.setFullYear(a,0,n),s.setHours(0,0,0,0),C(s,t)}function nr(e,t){const r=v(e,t==null?void 0:t.in),n=+C(r,t)-+rr(r,t);return Math.round(n/me)+1}function g(e,t){const r=e<0?"-":"",n=Math.abs(e).toString().padStart(t,"0");return r+n}const P={y(e,t){const r=e.getFullYear(),n=r>0?r:1-r;return g(t==="yy"?n%100:n,t.length)},M(e,t){const r=e.getMonth();return t==="M"?String(r+1):g(r+1,2)},d(e,t){return g(e.getDate(),t.length)},a(e,t){const r=e.getHours()/12>=1?"pm":"am";switch(t){case"a":case"aa":return r.toUpperCase();case"aaa":return r;case"aaaaa":return r[0];case"aaaa":default:return r==="am"?"a.m.":"p.m."}},h(e,t){return g(e.getHours()%12||12,t.length)},H(e,t){return g(e.getHours(),t.length)},m(e,t){return g(e.getMinutes(),t.length)},s(e,t){return g(e.getSeconds(),t.length)},S(e,t){const r=t.length,n=e.getMilliseconds(),a=Math.trunc(n*Math.pow(10,r-3));return g(a,t.length)}},T={midnight:"midnight",noon:"noon",morning:"morning",afternoon:"afternoon",evening:"evening",night:"night"},re={G:function(e,t,r){const n=e.getFullYear()>0?1:0;switch(t){case"G":case"GG":case"GGG":return r.era(n,{width:"abbreviated"});case"GGGGG":return r.era(n,{width:"narrow"});case"GGGG":default:return r.era(n,{width:"wide"})}},y:function(e,t,r){if(t==="yo"){const n=e.getFullYear(),a=n>0?n:1-n;return r.ordinalNumber(a,{unit:"year"})}return P.y(e,t)},Y:function(e,t,r,n){const a=ye(e,n),s=a>0?a:1-a;if(t==="YY"){const i=s%100;return g(i,2)}return t==="Yo"?r.ordinalNumber(s,{unit:"year"}):g(s,t.length)},R:function(e,t){const r=he(e);return g(r,t.length)},u:function(e,t){const r=e.getFullYear();return g(r,t.length)},Q:function(e,t,r){const n=Math.ceil((e.getMonth()+1)/3);switch(t){case"Q":return String(n);case"QQ":return g(n,2);case"Qo":return r.ordinalNumber(n,{unit:"quarter"});case"QQQ":return r.quarter(n,{width:"abbreviated",context:"formatting"});case"QQQQQ":return r.quarter(n,{width:"narrow",context:"formatting"});case"QQQQ":default:return r.quarter(n,{width:"wide",context:"formatting"})}},q:function(e,t,r){const n=Math.ceil((e.getMonth()+1)/3);switch(t){case"q":return String(n);case"qq":return g(n,2);case"qo":return r.ordinalNumber(n,{unit:"quarter"});case"qqq":return r.quarter(n,{width:"abbreviated",context:"standalone"});case"qqqqq":return r.quarter(n,{width:"narrow",context:"standalone"});case"qqqq":default:return r.quarter(n,{width:"wide",context:"standalone"})}},M:function(e,t,r){const n=e.getMonth();switch(t){case"M":case"MM":return P.M(e,t);case"Mo":return r.ordinalNumber(n+1,{unit:"month"});case"MMM":return r.month(n,{width:"abbreviated",context:"formatting"});case"MMMMM":return r.month(n,{width:"narrow",context:"formatting"});case"MMMM":default:return r.month(n,{width:"wide",context:"formatting"})}},L:function(e,t,r){const n=e.getMonth();switch(t){case"L":return String(n+1);case"LL":return g(n+1,2);case"Lo":return r.ordinalNumber(n+1,{unit:"month"});case"LLL":return r.month(n,{width:"abbreviated",context:"standalone"});case"LLLLL":return r.month(n,{width:"narrow",context:"standalone"});case"LLLL":default:return r.month(n,{width:"wide",context:"standalone"})}},w:function(e,t,r,n){const a=nr(e,n);return t==="wo"?r.ordinalNumber(a,{unit:"week"}):g(a,t.length)},I:function(e,t,r){const n=tr(e);return t==="Io"?r.ordinalNumber(n,{unit:"week"}):g(n,t.length)},d:function(e,t,r){return t==="do"?r.ordinalNumber(e.getDate(),{unit:"date"}):P.d(e,t)},D:function(e,t,r){const n=er(e);return t==="Do"?r.ordinalNumber(n,{unit:"dayOfYear"}):g(n,t.length)},E:function(e,t,r){const n=e.getDay();switch(t){case"E":case"EE":case"EEE":return r.day(n,{width:"abbreviated",context:"formatting"});case"EEEEE":return r.day(n,{width:"narrow",context:"formatting"});case"EEEEEE":return r.day(n,{width:"short",context:"formatting"});case"EEEE":default:return r.day(n,{width:"wide",context:"formatting"})}},e:function(e,t,r,n){const a=e.getDay(),s=(a-n.weekStartsOn+8)%7||7;switch(t){case"e":return String(s);case"ee":return g(s,2);case"eo":return r.ordinalNumber(s,{unit:"day"});case"eee":return r.day(a,{width:"abbreviated",context:"formatting"});case"eeeee":return r.day(a,{width:"narrow",context:"formatting"});case"eeeeee":return r.day(a,{width:"short",context:"formatting"});case"eeee":default:return r.day(a,{width:"wide",context:"formatting"})}},c:function(e,t,r,n){const a=e.getDay(),s=(a-n.weekStartsOn+8)%7||7;switch(t){case"c":return String(s);case"cc":return g(s,t.length);case"co":return r.ordinalNumber(s,{unit:"day"});case"ccc":return r.day(a,{width:"abbreviated",context:"standalone"});case"ccccc":return r.day(a,{width:"narrow",context:"standalone"});case"cccccc":return r.day(a,{width:"short",context:"standalone"});case"cccc":default:return r.day(a,{width:"wide",context:"standalone"})}},i:function(e,t,r){const n=e.getDay(),a=n===0?7:n;switch(t){case"i":return String(a);case"ii":return g(a,t.length);case"io":return r.ordinalNumber(a,{unit:"day"});case"iii":return r.day(n,{width:"abbreviated",context:"formatting"});case"iiiii":return r.day(n,{width:"narrow",context:"formatting"});case"iiiiii":return r.day(n,{width:"short",context:"formatting"});case"iiii":default:return r.day(n,{width:"wide",context:"formatting"})}},a:function(e,t,r){const a=e.getHours()/12>=1?"pm":"am";switch(t){case"a":case"aa":return r.dayPeriod(a,{width:"abbreviated",context:"formatting"});case"aaa":return r.dayPeriod(a,{width:"abbreviated",context:"formatting"}).toLowerCase();case"aaaaa":return r.dayPeriod(a,{width:"narrow",context:"formatting"});case"aaaa":default:return r.dayPeriod(a,{width:"wide",context:"formatting"})}},b:function(e,t,r){const n=e.getHours();let a;switch(n===12?a=T.noon:n===0?a=T.midnight:a=n/12>=1?"pm":"am",t){case"b":case"bb":return r.dayPeriod(a,{width:"abbreviated",context:"formatting"});case"bbb":return r.dayPeriod(a,{width:"abbreviated",context:"formatting"}).toLowerCase();case"bbbbb":return r.dayPeriod(a,{width:"narrow",context:"formatting"});case"bbbb":default:return r.dayPeriod(a,{width:"wide",context:"formatting"})}},B:function(e,t,r){const n=e.getHours();let a;switch(n>=17?a=T.evening:n>=12?a=T.afternoon:n>=4?a=T.morning:a=T.night,t){case"B":case"BB":case"BBB":return r.dayPeriod(a,{width:"abbreviated",context:"formatting"});case"BBBBB":return r.dayPeriod(a,{width:"narrow",context:"formatting"});case"BBBB":default:return r.dayPeriod(a,{width:"wide",context:"formatting"})}},h:function(e,t,r){if(t==="ho"){let n=e.getHours()%12;return n===0&&(n=12),r.ordinalNumber(n,{unit:"hour"})}return P.h(e,t)},H:function(e,t,r){return t==="Ho"?r.ordinalNumber(e.getHours(),{unit:"hour"}):P.H(e,t)},K:function(e,t,r){const n=e.getHours()%12;return t==="Ko"?r.ordinalNumber(n,{unit:"hour"}):g(n,t.length)},k:function(e,t,r){let n=e.getHours();return n===0&&(n=24),t==="ko"?r.ordinalNumber(n,{unit:"hour"}):g(n,t.length)},m:function(e,t,r){return t==="mo"?r.ordinalNumber(e.getMinutes(),{unit:"minute"}):P.m(e,t)},s:function(e,t,r){return t==="so"?r.ordinalNumber(e.getSeconds(),{unit:"second"}):P.s(e,t)},S:function(e,t){return P.S(e,t)},X:function(e,t,r){const n=e.getTimezoneOffset();if(n===0)return"Z";switch(t){case"X":return ae(n);case"XXXX":case"XX":return E(n);case"XXXXX":case"XXX":default:return E(n,":")}},x:function(e,t,r){const n=e.getTimezoneOffset();switch(t){case"x":return ae(n);case"xxxx":case"xx":return E(n);case"xxxxx":case"xxx":default:return E(n,":")}},O:function(e,t,r){const n=e.getTimezoneOffset();switch(t){case"O":case"OO":case"OOO":return"GMT"+ne(n,":");case"OOOO":default:return"GMT"+E(n,":")}},z:function(e,t,r){const n=e.getTimezoneOffset();switch(t){case"z":case"zz":case"zzz":return"GMT"+ne(n,":");case"zzzz":default:return"GMT"+E(n,":")}},t:function(e,t,r){const n=Math.trunc(+e/1e3);return g(n,t.length)},T:function(e,t,r){return g(+e,t.length)}};function ne(e,t=""){const r=e>0?"-":"+",n=Math.abs(e),a=Math.trunc(n/60),s=n%60;return s===0?r+String(a):r+String(a)+t+g(s,2)}function ae(e,t){return e%60===0?(e>0?"-":"+")+g(Math.abs(e)/60,2):E(e,t)}function E(e,t=""){const r=e>0?"-":"+",n=Math.abs(e),a=g(Math.trunc(n/60),2),s=g(n%60,2);return r+a+t+s}const ie=(e,t)=>{switch(e){case"P":return t.date({width:"short"});case"PP":return t.date({width:"medium"});case"PPP":return t.date({width:"long"});case"PPPP":default:return t.date({width:"full"})}},be=(e,t)=>{switch(e){case"p":return t.time({width:"short"});case"pp":return t.time({width:"medium"});case"ppp":return t.time({width:"long"});case"pppp":default:return t.time({width:"full"})}},ar=(e,t)=>{const r=e.match(/(P+)(p+)?/)||[],n=r[1],a=r[2];if(!a)return ie(e,t);let s;switch(n){case"P":s=t.dateTime({width:"short"});break;case"PP":s=t.dateTime({width:"medium"});break;case"PPP":s=t.dateTime({width:"long"});break;case"PPPP":default:s=t.dateTime({width:"full"});break}return s.replace("{{date}}",ie(n,t)).replace("{{time}}",be(a,t))},ir={p:be,P:ar},sr=/^D+$/,or=/^Y+$/,cr=["D","DD","YY","YYYY"];function ur(e){return sr.test(e)}function dr(e){return or.test(e)}function lr(e,t,r){const n=fr(e,t,r);if(console.warn(n),cr.includes(e))throw new RangeError(n)}function fr(e,t,r){const n=e[0]==="Y"?"years":"days of the month";return`Use \`${e.toLowerCase()}\` instead of \`${e}\` (in \`${t}\`) for formatting ${n} to the input \`${r}\`; see: https://github.com/date-fns/date-fns/blob/master/docs/unicodeTokens.md`}const mr=/[yYQqMLwIdDecihHKkms]o|(\w)\1*|''|'(''|[^'])+('|$)|./g,hr=/P+p+|P+|p+|''|'(''|[^'])+('|$)|./g,gr=/^'([^]*?)'?$/,yr=/''/g,br=/[a-zA-Z]/;function Dr(e,t,r){var d,f,l,h;const n=_(),a=n.locale??ge,s=n.firstWeekContainsDate??((f=(d=n.locale)==null?void 0:d.options)==null?void 0:f.firstWeekContainsDate)??1,i=n.weekStartsOn??((h=(l=n.locale)==null?void 0:l.options)==null?void 0:h.weekStartsOn)??0,o=v(e,r==null?void 0:r.in);if(!ft(o))throw new RangeError("Invalid time value");let c=t.match(hr).map(y=>{const b=y[0];if(b==="p"||b==="P"){const W=ir[b];return W(y,a.formatLong)}return y}).join("").match(mr).map(y=>{if(y==="''")return{isToken:!1,value:"'"};const b=y[0];if(b==="'")return{isToken:!1,value:wr(y)};if(re[b])return{isToken:!0,value:y};if(b.match(br))throw new RangeError("Format string contains an unescaped latin alphabet character `"+b+"`");return{isToken:!1,value:y}});a.localize.preprocessor&&(c=a.localize.preprocessor(o,c));const u={firstWeekContainsDate:s,weekStartsOn:i,locale:a};return c.map(y=>{if(!y.isToken)return y.value;const b=y.value;(dr(b)||ur(b))&&lr(b,t,String(e));const W=re[b[0]];return W(o,b,a.localize,u)}).join("")}function wr(e){const t=e.match(gr);return t?t[1].replace(yr,"'"):e}function vr(e,t,r){const n=_(),a=(r==null?void 0:r.locale)??n.locale??ge,s=2520,i=I(e,t);if(isNaN(i))throw new RangeError("Invalid time value");const o=Object.assign({},r,{addSuffix:r==null?void 0:r.addSuffix,comparison:i}),[c,u]=R(r==null?void 0:r.in,...i>0?[t,e]:[e,t]),d=pt(u,c),f=(A(u)-A(c))/1e3,l=Math.round((d-f)/60);let h;if(l<2)return r!=null&&r.includeSeconds?d<5?a.formatDistance("lessThanXSeconds",5,o):d<10?a.formatDistance("lessThanXSeconds",10,o):d<20?a.formatDistance("lessThanXSeconds",20,o):d<40?a.formatDistance("halfAMinute",0,o):d<60?a.formatDistance("lessThanXMinutes",1,o):a.formatDistance("xMinutes",1,o):l===0?a.formatDistance("lessThanXMinutes",1,o):a.formatDistance("xMinutes",l,o);if(l<45)return a.formatDistance("xMinutes",l,o);if(l<90)return a.formatDistance("aboutXHours",1,o);if(l<Z){const y=Math.round(l/60);return a.formatDistance("aboutXHours",y,o)}else{if(l<s)return a.formatDistance("xDays",1,o);if(l<H){const y=Math.round(l/Z);return a.formatDistance("xDays",y,o)}else if(l<H*2)return h=Math.round(l/H),a.formatDistance("aboutXMonths",h,o)}if(h=vt(u,c),h<12){const y=Math.round(l/H);return a.formatDistance("xMonths",y,o)}else{const y=h%12,b=Math.trunc(h/12);return y<3?a.formatDistance("aboutXYears",b,o):y<9?a.formatDistance("overXYears",b,o):a.formatDistance("almostXYears",b+1,o)}}function Or(e,t){return vr(e,dt(e),t)}export{xr as F,Dr as a,Or as f,Mr as z};

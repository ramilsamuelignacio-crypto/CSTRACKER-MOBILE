const http = require('http');
const fs = require('fs');
const path = require('path');
const api = require('./api/index');
const PORT = Number(process.env.PORT || 3000);
const PUBLIC = path.join(__dirname,'public');
const MIME = {'.html':'text/html; charset=utf-8','.js':'application/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json; charset=utf-8','.png':'image/png','.jpg':'image/jpeg','.jpeg':'image/jpeg','.svg':'image/svg+xml','.ico':'image/x-icon'};
function apiResponse(res){return {setHeader(name,value){res.setHeader(name,value);return this;},status(code){res.statusCode=code;return this;},json(data){res.setHeader('Content-Type','application/json; charset=utf-8');res.end(JSON.stringify(data));return this;},redirect(location){res.statusCode=302;res.setHeader('Location',location);res.end();}};}
function safeFile(urlPath){const requested=decodeURIComponent(urlPath.split('?')[0]);const rel=requested==='/'?'index.html':requested.replace(/^\//,'');const full=path.normalize(path.join(PUBLIC,rel));return full.startsWith(PUBLIC+path.sep)?full:null;}
const server=http.createServer(async (req,res)=>{
  if(req.url.startsWith('/api/')) return api(req,apiResponse(res));
  let file=safeFile(req.url); if(!file||!fs.existsSync(file)||fs.statSync(file).isDirectory()) file=path.join(PUBLIC,'index.html');
  const ext=path.extname(file); res.setHeader('Content-Type',MIME[ext]||'application/octet-stream'); fs.createReadStream(file).pipe(res);
});
require('./database').ready.then(()=>{
  server.listen(PORT,process.env.HOST||(process.env.PORT?'0.0.0.0':'127.0.0.1'),()=>console.log(`CSTRACKER running on port ${PORT}`));
}).catch(e=>{ console.error('Could not start: database connection failed:',e.message); process.exit(1); });

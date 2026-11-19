#!/usr/bin/env node
// Run from any directory. Default is a read-only local configuration check.
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {dirname,resolve} from 'node:path';
import {spawnSync} from 'node:child_process';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const config=JSON.parse(readFileSync(resolve(root,'app/google-services.json'),'utf8'));
const rc=JSON.parse(readFileSync(resolve(root,'.firebaserc'),'utf8'));
const project=config.project_info.project_id;
if(!project||! /^[a-z][a-z0-9-]{4,29}$/.test(project)||rc.projects.default!==project)throw new Error('Firebase project mismatch: .firebaserc and app/google-services.json must identify the same project.');
const expectedDatabaseRegion='asia-southeast1';
const databaseUrl=config.project_info.firebase_url||'';
if(databaseUrl && !databaseUrl.includes(expectedDatabaseRegion))throw new Error('Check the database region in functions/index.js against google-services.json before deployment.');
if(Number(process.versions.node.split('.')[0])<22)throw new Error('Use Node.js 22 or newer to run the deployment tools. Functions are configured for Node.js 22.');
const functions=['saveRequest','requestDelivery','requestOffer','notifyRequestEvent','notifyNearbyUsers','notifyOnChatMessage','syncSellerPostLocations','repeatAnnouncements','addCategory'];
console.log(`Project: ${project}\nCallable region: us-central1\nDatabase trigger region: ${expectedDatabaseRegion}`);
console.log('Voice is offline: this script is NOT needed for voice. It deploys the separate request/notification backend, which still requires Firebase billing. See DEPLOYMENT_BN.md.');
const executable=name=>name==='node'?process.execPath:name;
function run(command,args,cwd=root){
 const result=spawnSync(executable(command),args,{cwd,stdio:'inherit',shell:process.platform==='win32' && command!=='node'});
 if(result.error)throw result.error;
 if(result.status!==0)process.exit(result.status||1);
}
run('node',['--test','workflow.test.js','handlers.test.js'],resolve(root,'functions'));
if(!process.argv.includes('--deploy')){
 console.log('Local checks passed. After firebase login and cloud setup, run: node scripts/deploy-functions.mjs --deploy');
 process.exit(0);
}
run('npm',['ci'],resolve(root,'functions'));
run('firebase',['deploy','--project',project,'--only',functions.map(f=>'functions:'+f).join(',')]);
run('firebase',['functions:list','--project',project]);
console.log('Deployment completed. Complete the separate two-account request test in DEPLOYMENT_BN.md. Voice does not use these functions.');

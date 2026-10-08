#!/usr/bin/env python3
"""Run packaged Forge JARs in an isolated world; logs and fixture saves are retained.
Requires an existing Forge 1.20.1-47.4.20 server installation under --runtime/runtime.
"""
import argparse, hashlib, json, os, shlex, shutil, signal, subprocess, tempfile, threading, time
from pathlib import Path

V5_CASES = ['v5vanillafoodsgiveordinarybonuses', 'v5legendarypairsreplacenormalandusefourdistinctfoods', 'v5ordinarypairsandsplitstacksrespecttier', 'v5serverpreparationenforcestierandhidesstewoutput', 'v5bowldispenserfilterspersistsanddropsonce', 'v5everytableupgradesitsownage', 'v5workshoppurchasesareatomicandbeaconbound', 'v5gunbuffattributesuseonemodifierandcleanup', 'v5recoveryshoveldealszerodamageandknocksback', 'v5fuelweaponscannotbecrafted']

CASES = ['v6everyordinarytripledoubleswithoutlegendary','v6explicitrecipesandserverselection','v6sandwichbaseisseparateandatomic','v6milkrefillsreturnemptieswithoutloss','v6milkclearssavedmealsandeveryeffect','v6dispenserstockrequiresemptyhands']

REGRESSION_CASES = ['v4trapclasseswavesandpersistence', 'v4weaponsremaineffectiveaftertrapimmunity', 'v4sunlightprotectionkeepscombatfire', 'v4backendmodifierspreserveotherowners', 'v4nativereloadratetransitions', 'v3everyvanillafoodhasatraitandeveryeffectisreachable', 'v3pairbonusneedstwodifferentfoodsofonefamily', 'v3mealshowsasvanillaeffectsandfollowsthehomezone', 'v3recoveryhearthandspringystepactontheirdamage']

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--runtime',type=Path,required=True)
    ap.add_argument('--instance',type=Path);ap.add_argument('--optional-grenades',action='store_true')
    ap.add_argument('--cases',nargs='*',default=CASES);ap.add_argument('--baseline',action='store_true');ap.add_argument('--regressions',action='store_true')
    ap.add_argument('--restart-check',action='store_true');ap.add_argument('--seed',action='store_true');ap.add_argument('--client-smoke',action='store_true')
    args=ap.parse_args();repo=Path(__file__).resolve().parents[2];root=args.runtime.resolve()
    instance=args.instance or Path(tempfile.mkdtemp(prefix='messhall-v6-',dir=root/'test-runs'))
    if not args.instance:
        (instance/'libraries').symlink_to(root/'runtime/libraries',target_is_directory=True);(instance/'mods').mkdir()
        for jar in [repo/'custom-mods/arsenal-beacon/build/libs/arsenal-beacon-0.21.0-standalone.jar',root/'downloads/tacz-1.20.1-1.1.8-hotfix2.jar',root/'downloads/tacz-attributes-1.4.jar']:
            shutil.copy2(jar,instance/'mods'/jar.name)
        if args.optional_grenades:shutil.copy2(root/'downloads/lrtactical-1.20.1-0.4.3.jar',instance/'mods')
        (instance/'tacz').mkdir();shutil.copy2(root/'downloads/Apocalypse_v1.1.4_F.zip',instance/'tacz')
        (instance/'eula.txt').write_text('eula=true\n')
        (instance/'server.properties').write_text('server-ip=127.0.0.1\nserver-port='+('25577' if args.client_smoke else '25578')+'\nonline-mode=false\nlevel-name=world\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\nmax-tick-time=120000\n')
    manifest={p.name:hashlib.sha512(p.read_bytes()).hexdigest() for p in (instance/'mods').glob('*.jar')}
    (instance/'artifact-sha512.json').write_text(json.dumps(manifest,indent=2)+'\n')
    cmd=[str(Path(os.environ['JAVA_HOME'])/'bin/java'),*shlex.split(os.environ.get('GRADLE_OPTS','')),'-Xmx3G','-Darsenal.messHallTests=true',*(['-Darsenal.v6ClientTests=true'] if args.client_smoke else []),'@'+str(root/'runtime/libraries/net/minecraftforge/forge/1.20.1-47.4.20/unix_args.txt'),'nogui']
    proc=subprocess.Popen(cmd,cwd=instance,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
    lines=[];condition=threading.Condition();log_path=instance/('restart-console.log' if args.restart_check else 'server-console.log')
    def reader():
        with log_path.open('w') as log:
            for line in proc.stdout:
                log.write(line);log.flush()
                with condition:lines.append(line);condition.notify_all()
    thread=threading.Thread(target=reader,daemon=True);thread.start()
    def wait(expected,start=0,timeout=150):
        deadline=time.monotonic()+timeout
        with condition:
            while not any(expected in l for l in lines[start:]):
                failures=[l.strip() for l in lines[start:] if ('V4_TEST_FAIL' in l or 'V5_TEST_FAIL' in l or 'V6_TEST_FAIL' in l) or 'tests failed' in l or 'unexpected error occurred trying to execute' in l]
                if failures:raise RuntimeError('; '.join(failures))
                if proc.poll() is not None:raise RuntimeError(f'Server exited {proc.returncode}; {log_path}')
                if time.monotonic()>deadline:raise TimeoutError(f'Missing {expected}; {log_path}')
                condition.wait(1)
    def command(command,expected):
        start=len(lines);proc.stdin.write(command+'\n');proc.stdin.flush();wait(expected,start);print(expected,flush=True)
    results=[];print('INSTANCE '+str(instance),flush=True)
    def stop_signal(signum, frame):raise SystemExit(128+signum)
    signal.signal(signal.SIGTERM,stop_signal)
    try:
        wait('Done (',timeout=300);results.append('production startup');print('PRODUCTION_STARTUP_PASS',flush=True)
        if args.client_smoke:
            command('time set noon','Set the time to 6000');wait('V6_SERVER_UI_PASS',timeout=540);results.append('live client UI');time.sleep(5)
        if args.restart_check:command('mess-hall-fixture check','MESS_HALL_PERSIST_PASS');results.append('restart fixture')
        if args.baseline:
            for c,expected in [('mess-hall-test','All 5 mess hall tests passed'),('mess-hall-v2-test','All 6 mess hall v2 tests passed')]:command(c,expected);results.append(expected)
        for case in [*args.cases,*([*V5_CASES,*REGRESSION_CASES] if args.regressions else [])]:
            version='v4' if case.startswith(('v3','v4')) else 'v6' if case.startswith('v6') else 'v5';command('mess-hall-'+version+'-test '+case,version.upper()+'_TEST_PASS '+case);results.append(case)
        if args.seed:command('mess-hall-fixture seed','MESS_HALL_PERSIST_SEEDED')
    finally:
        (instance/('restart-results.json' if args.restart_check else 'run-results.json')).write_text(json.dumps({'passed':results,'jars':manifest},indent=2)+'\n')
        if proc.poll() is None:
            proc.stdin.write('stop\n');proc.stdin.flush()
            try:proc.wait(40)
            except subprocess.TimeoutExpired:proc.terminate();proc.wait(10)
        thread.join(5)
if __name__=='__main__':main()

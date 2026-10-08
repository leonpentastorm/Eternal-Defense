#!/usr/bin/env python3
"""Real Forge client kitchen UI smoke, using the retained official runtime prepared for this workspace."""
import argparse,ctypes,hashlib,json,os,platform,re,shlex,shutil,subprocess,sys,tempfile,time,uuid
from pathlib import Path

def allowed(rules):
    if not rules:return True
    enabled=False;features={'has_custom_resolution':True}
    for rule in rules:
        os_rule=rule.get('os',{});matches=os_rule.get('name','linux')=='linux'
        if 'arch' in os_rule:matches &= re.fullmatch(os_rule['arch'],platform.machine()) is not None
        if 'version' in os_rule:matches &= re.search(os_rule['version'],platform.release()) is not None
        matches &= all(features.get(k,False)==v for k,v in rule.get('features',{}).items())
        if matches:enabled=rule['action']=='allow'
    return enabled

def expand(items,values):
    result=[]
    for item in items:
        if isinstance(item,dict):
            if not allowed(item.get('rules')):continue
            item=item['value']
        for value in item if isinstance(item,list) else [item]:
            result.append(re.sub(r'\$\{([^}]+)\}',lambda match:values[match.group(1)],value))
    return result

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--food-tooltip-jar',type=Path);parser.add_argument('--gui-scale',type=int,default=3);args=parser.parse_args();root=args.runtime.resolve();repo=Path(__file__).resolve().parents[2]
    instance=Path(tempfile.mkdtemp(prefix='messhall-v6-client-',dir=root/'test-runs'));(instance/'mods').mkdir();(instance/'config').mkdir();(instance/'screenshots').mkdir()
    for jar in [repo/'custom-mods/arsenal-beacon/build/libs/arsenal-beacon-0.21.0-standalone.jar',root/'downloads/tacz-1.20.1-1.1.8-hotfix2.jar',root/'downloads/tacz-attributes-1.4.jar']:shutil.copy2(jar,instance/'mods'/jar.name)
    if args.food_tooltip_jar:shutil.copy2(args.food_tooltip_jar,instance/'mods')
    (instance/'artifact-sha512.json').write_text(json.dumps({p.name:hashlib.sha512(p.read_bytes()).hexdigest() for p in (instance/'mods').glob('*.jar')},indent=2)+'\n')
    (instance/'tacz').mkdir();shutil.copy2(root/'downloads/Apocalypse_v1.1.4_F.zip',instance/'tacz')
    (instance/'options.txt').write_text(f'onboardAccessibility:false\ntutorialStep:none\nskipMultiplayerWarning:true\nguiScale:{args.gui_scale}\nrenderDistance:3\nsimulationDistance:5\nmaxFps:40\ngamma:1.0\n')
    (instance/'config/fml.toml').write_text('earlyWindowControl = false\nmaxThreads = 2\n')
    server_log=(instance/'server-runner.log').open('w');server=subprocess.Popen([sys.executable,str(repo/'tools/qa/run-v6-server.py'),'--runtime',str(root),'--client-smoke','--cases'],stdout=server_log,stderr=subprocess.STDOUT)
    display=None;client=None
    try:
        deadline=time.monotonic()+360
        while 'PRODUCTION_STARTUP_PASS' not in (instance/'server-runner.log').read_text().splitlines():
            if server.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Server startup failed: '+str(instance/'server-runner.log'))
            time.sleep(.5)
        display=subprocess.Popen([str(root/'tools/xvfb/usr/bin/Xvfb'),'-displayfd','1','-screen','0','1280x720x24','-nolisten','tcp'],stdout=subprocess.PIPE,stderr=(instance/'xvfb.log').open('w'),text=True)
        number=display.stdout.readline().strip()
        if not number.isdigit():raise RuntimeError('Xvfb failed')
        env=dict(os.environ,DISPLAY=':'+number,LIBGL_ALWAYS_SOFTWARE='1',LP_NUM_THREADS='2',MESA_SHADER_CACHE_DIR=str(root/'cache/mesa'),ALSOFT_DRIVERS='null')
        launch=json.loads((root/'runtime/client-launch.json').read_text());player='ArsenalDev'
        values={'auth_player_name':player,'auth_uuid':uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+player).encode()).digest(),version=3).hex,'auth_access_token':'0','auth_xuid':'','clientid':'','user_type':'legacy','version_name':'1.20.1-forge-47.4.20','version_type':'release','game_directory':str(instance),'assets_root':str(root/'runtime/assets'),'assets_index_name':launch['assetIndex'],'natives_directory':str(root/'runtime/natives'),'library_directory':str(root/'runtime/libraries'),'classpath_separator':os.pathsep,'classpath':os.pathsep.join(launch['classpath']),'launcher_name':'ArsenalLocalTest','launcher_version':'1','resolution_width':'1280','resolution_height':'720'}
        command=[str(Path(os.environ['JAVA_HOME'])/'bin/java'),*shlex.split(os.environ.get('GRADLE_OPTS','')),'-Xmx4G','-XX:-CreateCoredumpOnCrash','-Darsenal.v6ClientTests=true',*expand(launch['jvm'],values),launch['mainClass'],*expand(launch['game'],values),'--quickPlayMultiplayer','127.0.0.1:25577']
        xlib=ctypes.CDLL('libX11.so.6');xlib.XOpenDisplay.argtypes=[ctypes.c_char_p];xlib.XOpenDisplay.restype=ctypes.c_void_p
        xlib.XDefaultRootWindow.argtypes=[ctypes.c_void_p];xlib.XDefaultRootWindow.restype=ctypes.c_ulong
        xlib.XWarpPointer.argtypes=[ctypes.c_void_p,ctypes.c_ulong,ctypes.c_ulong,ctypes.c_int,ctypes.c_int,ctypes.c_uint,ctypes.c_uint,ctypes.c_int,ctypes.c_int];xlib.XFlush.argtypes=[ctypes.c_void_p]
        pointer_display=xlib.XOpenDisplay(env['DISPLAY'].encode())
        if not pointer_display:raise RuntimeError('Cannot open QA display')
        pointer_root=xlib.XDefaultRootWindow(pointer_display);pointer_seen=0
        log_path=instance/'client-console.log';client=subprocess.Popen(command,cwd=instance,env=env,stdout=log_path.open('w'),stderr=subprocess.STDOUT)
        print('CLIENT_INSTANCE '+str(instance),flush=True);print('DISPLAY '+env['DISPLAY'],flush=True);deadline=time.monotonic()+600;captured=False
        while time.monotonic()<deadline:
            if client.poll() is not None:raise RuntimeError('Client exited: '+str(log_path))
            if server.poll() is not None:raise RuntimeError('QA server exited: '+str(instance/'server-runner.log'))
            text=log_path.read_text(errors='replace')
            requests=re.findall(r'V6_QA_CURSOR (\d+) (\d+)',text)
            for at,(x,y) in enumerate(requests[pointer_seen:],start=pointer_seen):
                # First screenshot shows the empty bread base unobscured; later phases cover item tooltips.
                if at==0:x=y='15'
                xlib.XWarpPointer(pointer_display,0,pointer_root,0,0,0,0,int(x),int(y));xlib.XFlush(pointer_display)
            pointer_seen=len(requests)
            if not captured and time.monotonic()>deadline-300:
                subprocess.run(['import','-display',env['DISPLAY'],'-window','root',str(instance/'screenshots/startup.png')],check=True,timeout=10);captured=True
            if 'V6_CLIENT_UI_PASS' in text:
                time.sleep(.5)
                subprocess.run(['import','-display',env['DISPLAY'],'-window','root',str(instance/'screenshots/ui-complete.png')],check=True,timeout=10)
                print(next(line for line in text.splitlines() if 'V6_CLIENT_UI_PASS' in line),flush=True);return
            if 'IllegalStateException' in text or 'V6_UI_FAIL' in text:raise RuntimeError('UI observation failed: '+str(log_path))
            time.sleep(.2)
        raise TimeoutError('Client UI fixture timed out: '+str(log_path))
    finally:
        for proc in [client,display,server]:
            if proc and proc.poll() is None:
                proc.terminate()
                try:proc.wait(55 if proc is server else 15)
                except subprocess.TimeoutExpired:proc.kill();proc.wait()
        server_log.close()
if __name__=='__main__':main()

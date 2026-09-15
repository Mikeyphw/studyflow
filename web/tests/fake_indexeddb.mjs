export class FakeIndexedDBFactory {
  constructor() { this.databases = new Map(); }
  open(name, version) {
    const request = {};
    setTimeout(() => {
      let db = this.databases.get(name);
      const needsUpgrade = !db;
      if (!db) { db = new FakeDatabase(name, version); this.databases.set(name, db); }
      request.result = db;
      if (needsUpgrade) request.onupgradeneeded?.({ target: request });
      request.onsuccess?.({ target: request });
    }, 0);
    return request;
  }
}

class FakeDatabase {
  constructor(name, version) { this.name=name; this.version=version; this.stores=new Map(); this.keyPaths=new Map(); }
  get objectStoreNames() { return { contains: (name) => this.stores.has(name) }; }
  createObjectStore(name, options={}) { if(!this.stores.has(name)) this.stores.set(name,new Map()); this.keyPaths.set(name, options.keyPath ?? null); return {}; }
  transaction(names, mode) { return new FakeTransaction(this, Array.isArray(names)?names:[names], mode); }
  close() {}
}

class FakeTransaction {
  constructor(db,names,mode){ this.db=db; this.names=names; this.mode=mode; this.error=null; this.completed=false; this.scheduled=false; }
  objectStore(name){ if(!this.names.includes(name)) throw new Error(`store not in transaction: ${name}`); return new FakeStore(this,name); }
  abort(){ this.error=this.error || new Error("transaction aborted"); this.onabort?.(); }
  scheduleComplete(){ if(this.scheduled)return; this.scheduled=true; setTimeout(()=>{this.completed=true; this.oncomplete?.();},0); }
}

class FakeStore {
  constructor(tx,name){this.tx=tx;this.name=name;this.map=tx.db.stores.get(name);}
  request(action){const req={}; queueMicrotask(()=>{try{req.result=action();req.onsuccess?.();this.tx.scheduleComplete();}catch(error){req.error=error;this.tx.error=error;req.onerror?.();this.tx.onerror?.();}});return req;}
  get(key){return this.request(()=>this.map.get(key));}
  getAll(){return this.request(()=>[...this.map.values()]);}
  put(value,key){const keyPath=this.tx.db.keyPaths.get(this.name);const resolved=key ?? (keyPath?value?.[keyPath]:undefined);if(resolved==null)throw new Error('missing key');const copy=value;this.map.set(resolved,copy);this.tx.scheduleComplete();return {result:resolved};}
  delete(key){this.map.delete(key);this.tx.scheduleComplete();return {result:undefined};}
  clear(){this.map.clear();this.tx.scheduleComplete();return {result:undefined};}
}

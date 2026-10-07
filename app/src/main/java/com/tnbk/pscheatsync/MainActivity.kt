package com.tnbk.pscheatsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

data class Cheat(val platform:String,val fmt:String,val file:String,val game:String)

class MainActivity:AppCompatActivity(){
 private lateinit var folderText:TextView; private lateinit var status:TextView; private lateinit var output:TextView
 private lateinit var progress:ProgressBar; private lateinit var search:EditText
 private lateinit var ps4:CheckBox; private lateinit var ps5:CheckBox; private lateinit var json:CheckBox; private lateinit var mc4:CheckBox; private lateinit var shn:CheckBox
 private var tree:Uri?=null; private val catalog=mutableListOf<Cheat>()
 private val sources=mapOf("PS4" to "https://raw.githubusercontent.com/GoldHEN/GoldHEN_Cheat_Repository/main","PS5" to "https://raw.githubusercontent.com/etaHEN/PS5_Cheats/main")

 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main)
  folderText=findViewById(R.id.folderText);status=findViewById(R.id.status);output=findViewById(R.id.list);progress=findViewById(R.id.progress);search=findViewById(R.id.search)
  ps4=findViewById(R.id.ps4);ps5=findViewById(R.id.ps5);json=findViewById(R.id.json);mc4=findViewById(R.id.mc4);shn=findViewById(R.id.shn)
  val saved=getPreferences(MODE_PRIVATE).getString("tree",null);if(saved!=null){tree=Uri.parse(saved);folderText.text=saved}
  findViewById<Button>(R.id.pickFolder).setOnClickListener{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),77)}
  findViewById<Button>(R.id.check).setOnClickListener{syncIndex()}
  findViewById<Button>(R.id.download).setOnClickListener{downloadMissing()}
  search.setOnEditorActionListener{_,_,_->render();true}
 }
 override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==77&&c==RESULT_OK)d?.data?.let{u->
  tree=u;contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
  getPreferences(MODE_PRIVATE).edit().putString("tree",u.toString()).apply();folderText.text=u.toString()
 }}
 private fun platforms()=listOfNotNull(if(ps4.isChecked)"PS4" else null,if(ps5.isChecked)"PS5" else null)
 private fun formats()=listOfNotNull(if(json.isChecked)"json" else null,if(mc4.isChecked)"mc4" else null,if(shn.isChecked)"shn" else null)
 private fun getText(url:String):String{val c=URL(url).openConnection() as HttpURLConnection;c.setRequestProperty("User-Agent","PS-Cheats-Smart-Sync-Android/1.0");c.connectTimeout=15000;c.readTimeout=30000;return c.inputStream.bufferedReader().use{it.readText()}}
 private fun parse(t:String,p:String,f:String)=t.lineSequence().mapNotNull{line->val x=line.trim();if(x.isBlank()||!x.contains("="))null else{val file=x.substringBefore("=").trim();val game=x.substringAfter("=").trim();if(file.lowercase().endsWith("."+f)||(f=="mc4"&&file.lowercase().endsWith(".mc4.xml")))Cheat(p,f,file,game) else null}}.toList()
 private fun syncIndex(){val ps=platforms();val fs=formats();if(ps.isEmpty()||fs.isEmpty()){toast("เลือกเครื่องและประเภทไฟล์ก่อน");return}
  lifecycleScope.launch{status.text="กำลังตรวจสอบฐานข้อมูล...";progress.progress=0
   try{val all=withContext(Dispatchers.IO){val a=mutableListOf<Cheat>();var n=0;val total=ps.size*fs.size
    for(p in ps)for(f in fs){a+=parse(getText("${sources[p]}/$f.txt"),p,f);n++;withContext(Dispatchers.Main){progress.progress=n*100/total}};a.distinctBy{it.platform+"|"+it.fmt+"|"+it.file}}
    catalog.clear();catalog.addAll(all);render();status.text="พบ ${catalog.size} รายการ";progress.progress=100
   }catch(e:Exception){status.text="ผิดพลาด: ${e.message}"}
  }
 }
 private fun render(){val q=search.text.toString().trim().lowercase();val rows=if(q.isBlank())catalog else catalog.filter{(it.file+" "+it.game).lowercase().contains(q)}
  output.text=rows.take(1500).joinToString("\n"){ "[${it.platform}] ${it.fmt.uppercase()}  ${it.file}\n    ${it.game}" }+if(rows.size>1500)"\n... ${rows.size-1500} รายการเพิ่มเติม" else ""
 }
 private fun dir(root:DocumentFile,name:String)=root.findFile(name)?:root.createDirectory(name)!!
 private fun exists(root:DocumentFile,c:Cheat):Boolean{val p=dir(root,c.platform);val f=dir(p,c.fmt);val local=if(c.file.endsWith(".mc4.xml",true))c.file.dropLast(4) else c.file;return f.findFile(local)!=null}
 private fun downloadMissing(){val u=tree?:run{toast("เลือกโฟลเดอร์ปลายทางก่อน");return};if(catalog.isEmpty()){toast("กดตรวจสอบก่อน");return}
  lifecycleScope.launch{try{val root=DocumentFile.fromTreeUri(this@MainActivity,u)?:throw Exception("เปิดโฟลเดอร์ไม่ได้");val q=search.text.toString().trim().lowercase()
   val targets=catalog.filter{(q.isBlank()||(it.file+" "+it.game).lowercase().contains(q))&&!exists(root,it)}
   if(targets.isEmpty()){status.text="ไฟล์ครบแล้ว";return@launch};var ok=0
   targets.forEachIndexed{i,c->status.text="ดาวน์โหลด ${i+1}/${targets.size}: ${c.file}";progress.progress=i*100/targets.size
    withContext(Dispatchers.IO){val p=dir(root,c.platform);val f=dir(p,c.fmt);val local=if(c.file.endsWith(".mc4.xml",true))c.file.dropLast(4) else c.file
     val conn=URL("${sources[c.platform]}/${c.fmt}/${c.file}").openConnection() as HttpURLConnection;conn.setRequestProperty("User-Agent","PS-Cheats-Smart-Sync-Android/1.0")
     val bytes=conn.inputStream.use{it.readBytes()};val old=f.findFile(local);old?.delete();val out=f.createFile("application/octet-stream",local)?:throw Exception("สร้าง $local ไม่ได้")
     contentResolver.openOutputStream(out.uri,"w")!!.use{it.write(bytes)}
    };ok++
   };progress.progress=100;status.text="เสร็จแล้ว ✓ $ok ไฟล์"
  }catch(e:Exception){status.text="ผิดพลาด: ${e.message}"}
  }
 }
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}
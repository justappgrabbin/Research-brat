package org.stellarproximology.researchbrat;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.webkit.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.JSONObject;

public class MainActivity extends Activity {
  private WebView web;
  private final ExecutorService pool=Executors.newCachedThreadPool();
  private static final int PICK=401,SAVE=402;
  private String pendingText="",pendingName="report.md";

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    web=new WebView(this); setContentView(web);
    WebSettings s=web.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowContentAccess(true); s.setAllowFileAccess(true);
    web.setWebViewClient(new WebViewClient());
    web.addJavascriptInterface(new Bridge(),"Native");
    web.loadUrl("file:///android_asset/index.html");
  }

  private void emit(String fn,String payload){
    runOnUiThread(()->web.evaluateJavascript("window."+fn+"&&window."+fn+"("+JSONObject.quote(payload)+")",null));
  }

  public class Bridge {
    @JavascriptInterface public void fetch(String id,String url){
      pool.submit(()->{
        try{
          URLConnection c=new URL(url).openConnection();
          c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setRequestProperty("User-Agent","ResearchBrat/0.1");
          StringBuilder out=new StringBuilder();
          try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)out.append(line).append('\n');}
          emit("onNativeFetch",new JSONObject().put("id",id).put("ok",true).put("body",out.toString()).toString());
        }catch(Exception e){try{emit("onNativeFetch",new JSONObject().put("id",id).put("ok",false).put("error",e.toString()).toString());}catch(Exception ignored){}}
      });
    }

    @JavascriptInterface public void openDrivePicker(){
      runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,PICK);});
    }

    @JavascriptInterface public void saveText(String name,String text){
      pendingName=(name==null||name.isEmpty())?"report.md":name; pendingText=text==null?"":text;
      runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,pendingName);startActivityForResult(i,SAVE);});
    }

    @JavascriptInterface public void openExternal(String url){runOnUiThread(()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))));}
  }

  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
    super.onActivityResult(requestCode,resultCode,data);
    if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
    Uri uri=data.getData();
    if(requestCode==PICK){
      pool.submit(()->{
        try{
          String name="Drive file"; Cursor c=getContentResolver().query(uri,null,null,null,null);
          if(c!=null){int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0&&c.moveToFirst())name=c.getString(n);c.close();}
          ByteArrayOutputStream bos=new ByteArrayOutputStream();
          try(InputStream in=getContentResolver().openInputStream(uri)){byte[] buf=new byte[8192];int n,cap=4*1024*1024;while((n=in.read(buf))>0&&bos.size()<cap)bos.write(buf,0,n);}
          emit("onDriveImport",new JSONObject().put("name",name).put("text",new String(bos.toByteArray(),StandardCharsets.UTF_8)).toString());
        }catch(Exception e){try{emit("onDriveImport",new JSONObject().put("error",e.toString()).toString());}catch(Exception ignored){}}
      });
    } else if(requestCode==SAVE){try(OutputStream out=getContentResolver().openOutputStream(uri)){out.write(pendingText.getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}
  }
}

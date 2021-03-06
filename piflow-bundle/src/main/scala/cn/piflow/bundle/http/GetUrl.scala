package cn.piflow.bundle.http

import java.util

import cn.piflow.conf.bean.PropertyDescriptor
import cn.piflow.conf.util.{ImageUtil, MapUtil}
import cn.piflow.conf.{ConfigurableStop, PortEnum, StopGroup}
import cn.piflow.{JobContext, JobInputStream, JobOutputStream, ProcessContext}
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet}
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.dom4j.{Document, DocumentHelper, Element}

import scala.collection.JavaConverters._
import scala.collection.mutable.{ArrayBuffer, ListBuffer}


class GetUrl extends ConfigurableStop{
  override val authorEmail: String = "ygang@cnic.com"
  override val description: String = "Http Get"

  override val inportList: List[String] = List(PortEnum.NonePort.toString)
  override val outportList: List[String] = List(PortEnum.DefaultPort.toString)


  var url :String= _
  var types :String = _

  // xml String
  var label:String=_
  var schema: String = _

  override def perform(in: JobInputStream, out: JobOutputStream, pec: JobContext): Unit = {

    val ss = pec.get[SparkSession]()

    // get from url
    val client = HttpClients.createDefault()
    val getFlowInfo:HttpGet = new HttpGet(url)

    val response:CloseableHttpResponse = client.execute(getFlowInfo)
    val entity = response.getEntity
    val jsonString = EntityUtils.toString(entity,"UTF-8")
    if (types == "json"){

      // json to df
      val jsonRDD = ss.sparkContext.makeRDD(jsonString :: Nil)
      val jsonDF = ss.read.json(jsonRDD)

      out.write(jsonDF)
    }


    if(types=="xml"){
      val doc: Document = DocumentHelper.parseText(jsonString)
      val rootElt: Element = doc.getRootElement
      var arrbuffer:ArrayBuffer[Element]=ArrayBuffer()
      arrbuffer+=rootElt

      val arrLabel: Array[String] = label.split(",")
      for(x<-(1 until arrLabel.length)){
        var ele: Element =null
        if(x==1){
          ele = rootElt.element(arrLabel(x).toString)
        }else{
          ele = arrbuffer(x-2).element(arrLabel(x).toString)
        }
        arrbuffer+=ele
      }

      val FatherElement: Element = arrbuffer(arrbuffer.size-2)

      val arrSchame: Array[String] = schema.split(",")

      var list:ListBuffer[String]=ListBuffer()


      val FatherInterator: util.Iterator[_] = FatherElement.elementIterator(arrbuffer.last.getName)
      val scalaIterator: Iterator[Element] = FatherInterator.asInstanceOf[util.Iterator[Element]].asScala

      while (scalaIterator.hasNext){
        val value: Element = scalaIterator.next()
        var text: String =""
        for(each<-arrSchame){
          text += value.element(each).getText+","
        }
        list.+=(text.substring(0,text.length-1))
      }

      val listRows: List[Row] = list.toList.map(line => {
        val seq: Seq[String] = line.split(",").toSeq
        val row = Row.fromSeq(seq)
        row
      })
      val rowRDD: RDD[Row] = ss.sparkContext.makeRDD(listRows)


      val fields: Array[StructField] = arrSchame.map(p => {
        StructField(p, StringType, nullable = true)
      })
      val structType: StructType = StructType(fields)

      val outDf: DataFrame = ss.createDataFrame(rowRDD,structType)

      out.write(outDf)
    }




  }
  override def setProperties(map: Map[String, Any]): Unit = {
    url = MapUtil.get(map,key="url").asInstanceOf[String]
    types= MapUtil.get(map,key="types").asInstanceOf[String]
    label = MapUtil.get(map,"label").asInstanceOf[String]
    schema = MapUtil.get(map,"schema").asInstanceOf[String]

  }

  override def getPropertyDescriptor(): List[PropertyDescriptor] = {
    var descriptor : List[PropertyDescriptor] = List()
    val url = new PropertyDescriptor().name("url").displayName("url").defaultValue("").required(true)
    val types = new PropertyDescriptor().name("types").displayName("types").defaultValue("the url content is json or xml)").required(true)
    val label = new PropertyDescriptor().name("label").displayName("label").description("label path for hope,the delimiter is ,").defaultValue("").required(true)
    descriptor = label :: descriptor
    val schema = new PropertyDescriptor().name("schema").displayName("schema").description("name of field in label,the delimiter is ,").defaultValue("").required(true)
    descriptor = schema :: descriptor

    descriptor = types :: descriptor
    descriptor = url :: descriptor
    descriptor
  }

  override def getIcon(): Array[Byte] = {
    ImageUtil.getImage("icon/http/GetUrl.png")
  }

  override def getGroup(): List[String] = {
    List(StopGroup.HttpGroup.toString)
  }

  override def initialize(ctx: ProcessContext): Unit = {

  }

}

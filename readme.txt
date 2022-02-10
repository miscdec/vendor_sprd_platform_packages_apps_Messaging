1、由于libs/ctcc/ims.jar包中的org.mozilla类和 libs/cucc/rhino-1.7.7.1.jar中的org.mozilla类重复了，又由于libs/cucc/rhino-1.7.7.1.jar中只有org.mozilla类，所以把libs/cucc/rhino-1.7.7.1.jar重名成libs/cucc/rhino-1.7.7.1.jar.bak，不使其参与编译
   libs/cmcc/smsparsing/android-support-v4.jar 在工程中已经有了，所以也去掉，否则会导致编译报错： 
				   错误: 找不到符号
						符号:   方法 setChannelId(String)
						位置: 类型为Builder的变量 builder	
						
2、为了解决res和res_sprd中的资源冲突，再考虑到res_sprd比res优先级高，所以把res_sprd分别放到debug和release中去

3、res_cmcc_sso和res_smsparsing中有很多资源重复定义的问题，冲突的资源的值均相同，所以注释掉res_smsparsing\values目录中的这三个文件smssdk_colors.xml、smssdk_dimens.xml、smssdk_ids.xml中定义的资源。

4、如果一个JNI库依赖另一个静态库（动态库），除了在Android.mk中增加：
		LOCAL_STATIC_LIBRARIES ：= libxxx
		
		还要在这个Android.mk的最后面增加下面一行代码：
		include $(call all-makefiles-under, $(libs_path))   //libs_path 代表依赖库的路径
		
		以libframesequence为例，libframesequence会依赖libgif静态库，所以需要在Android.mk文件中增加：
		LOCAL_STATIC_LIBRARIES := libgif
		同时，在Android.mk文件最后面增加：
		include $(call all-makefiles-under, $(LIBS_PATH))   //LIBS_PATH 代表依赖库的路径,这里的库路径是libs
		
5、静态库的使用说明
		主旨：需要考虑两种情况，分别是用Android Studio编译和基于工程编译（使用make messaging命令），要尽可能兼容这两种情况，尽可能的复用静态库。
		涉及到的静态库有
				chips(com.android.ex.chips): frameworks/opt/chips
				common(com.android.common): frameworks/ex/common
				framesequence(android.support.rastermill): frameworks/ex/framesequence
				photoviewer(com.android.ex.photo): frameworks/opt/photoviewer
				vcard(com.android.vcard): frameworks/opt/vcard
				giflib: external/giflib  
				guava: external/guava
				jsr305: external/jsr305
				libphonenumber: external/libphonenumber
				recyclerview-v7
				appcompat-v7
				库 giflib 和 framesequence: 因为涉及到Android.mk文件的修改，为了不修改其它仓库的Android.mk文件, AS编译和基于工程编译均使用Messaging/libs/下面的库
				由于chips库尚不稳定，AS和基于工程编译时统一调用Messaging/libs/chips，以方便代码的维护；
				除此之外，均共享工程中已有的库。
				注意： 如果准备将Messging拷贝到其它目录下（如放到本地磁盘D:\)调试,就需要通过 File -> New -> Import Module的方式，将相应的静态库(common、photoviewer、vcard、guava、jsr305、libphonenumber)导入进来，导入的时候，会将源码拷贝到app同级目录，再将它们移到libs目录，再将settings.gradle中useLocalLibs的值设置为true
				提示：在工程目录（vendor\sprd\platform\packages\apps\Messaging）下面调试，不要导入静态库，否则在执行make messaging时会出现库重复定义的错误
				
		涉及的动态库有
				libs/framework.jar
    		libs/telephony-common.jar
    		libs/radio_interactor_common.jar
    主要是为了能够正常编译而导入的，均产生于sprdroid9.0_trunk

6、framesequence 与系统库重复，现已将framesequence整个文件夹删掉，并将其打包为framesequence.7z压缩文件，放在libs文件夹路径下。将Messaging文件夹下
     的Android.mk、MmsFolderView/Android.mk文件中的libframesequence-ex与android-common-framesequence-ex分别修改为libframesequence与android-common-framesequence。修改后的sprdroidq_trunk，
     不能通过AS编译，如需AS编译调试，还需将framesequence.7z解压（就解压在当前路径下），并将Messaging文件夹下的Android.mk、MmsFolderView/Android.mk等文件恢复到之前，即重新将libframesequence修改
     为libframesequence-ex，android-common-framesequence修改为android-common-framesequence-ex。


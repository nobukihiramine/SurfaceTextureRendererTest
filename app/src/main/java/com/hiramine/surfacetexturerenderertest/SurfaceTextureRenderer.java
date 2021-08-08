package com.hiramine.surfacetexturerenderertest;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import java.nio.FloatBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

class SurfaceTextureRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener
{
	// 定数
	private final static String TAG = "SurfaceTextureRenderer";

	// テクスチャの大きさ
	private       int            m_iTextureWidth;
	private       int            m_iTextureHeight;
	// GL関連
	private       int            m_iProgram;
	private       int            m_iPositionAttributeLocation;
	private       int            m_iTexCoordAttributeLocation;
	private       int            m_iMVPMatrixUniformLocation;
	private       int            m_iSTMatrixUniformLocation;
	private       int            m_iTextureID;
	private final FloatBuffer    m_fbVertex;
	private final float[]        m_f16MVPMatrix = new float[16];
	private final float[]        m_f16STMatrix  = new float[16];
	// Surface関連
	private       Surface        m_surface;
	private       SurfaceTexture m_surfacetexture;
	private       boolean        m_bNewFrameAvailable;
	// ランダムサークル描画用メンバ変数
	private final Rect           m_rectTexture  = new Rect();
	private final Paint          m_paint        = new Paint();
	private final Random         m_random       = new Random();
	private final Handler        m_handler      = new Handler( Looper.getMainLooper() );

	// シェーダーコード
	private final String m_strVertexShaderCode =
			"uniform mat4 uniformModelViewProjectionMatrix;\n" +
			"uniform mat4 uniformSurfaceTextureMatrix;\n" +
			"attribute vec4 attributePosition;\n" +
			"attribute vec4 attributeTexCoord;\n" +
			"varying vec2 varyingTexCoord;\n" +
			"void main() {\n" +
			"  gl_Position = uniformModelViewProjectionMatrix * attributePosition;\n" +
			"  varyingTexCoord = (uniformSurfaceTextureMatrix * attributeTexCoord).xy;\n" +
			"}\n";

	// 1行目：GL_TEXTURE_EXTERNAL_OESテクスチャを使用するための宣言。
	// 4行目：sampler2D変数の代わりにsamplerExternalOES変数を定義。
	private final String m_strFragmentShaderCode =
			"#extension GL_OES_EGL_image_external : require\n" +
			"precision mediump float;\n" +
			"varying vec2 varyingTexCoord;\n" +
			"uniform samplerExternalOES uniformTexture;\n" +
			"void main() {\n" +
			"  gl_FragColor = texture2D(uniformTexture, varyingTexCoord);\n" +
			"}\n";

	// コンストラクタ
	public SurfaceTextureRenderer()
	{
		Log.d( TAG, "Constructor" );
		Log.d( TAG, "Thread name = " + Thread.currentThread().getName() );    // Thread name = main

		float[] m_afVertex = {
				// X, Y, Z, U, V
				-1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
				1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
				-1.0f, 1.0f, 0.0f, 0.0f, 1.0f,
				1.0f, 1.0f, 0.0f, 1.0f, 1.0f,
				};
		m_fbVertex = MyUtils.makeFloatBuffer( m_afVertex );

		Matrix.setIdentityM( m_f16MVPMatrix, 0 );
		Matrix.setIdentityM( m_f16STMatrix, 0 );
	}

	@Override
	public void onSurfaceCreated( GL10 gl10, EGLConfig eglConfig )
	{
		Log.d( TAG, "onSurfaceCreated" );
		Log.d( TAG, "Thread name = " + Thread.currentThread().getName() );    // Thread name = GLThread XXXX

		m_iProgram = MyUtils.createProgram( m_strVertexShaderCode, m_strFragmentShaderCode );
		if( 0 == m_iProgram )
		{
			return;
		}

		// AttributeLocation
		m_iPositionAttributeLocation = GLES20.glGetAttribLocation( m_iProgram, "attributePosition" );
		MyUtils.checkGlError( "glGetAttribLocation attributePosition" );
		m_iTexCoordAttributeLocation = GLES20.glGetAttribLocation( m_iProgram, "attributeTexCoord" );
		MyUtils.checkGlError( "glGetAttribLocation attributeTexCoord" );

		// UniformLocation
		m_iMVPMatrixUniformLocation = GLES20.glGetUniformLocation( m_iProgram, "uniformModelViewProjectionMatrix" );
		MyUtils.checkGlError( "glGetUniformLocation uniformModelViewProjectionMatrix" );
		m_iSTMatrixUniformLocation = GLES20.glGetUniformLocation( m_iProgram, "uniformSurfaceTextureMatrix" );
		MyUtils.checkGlError( "glGetUniformLocation uniformSurfaceTextureMatrix" );

		// Texture
		int[] aiTextureID = new int[1];
		GLES20.glGenTextures( 1, aiTextureID, 0 );
		m_iTextureID = aiTextureID[0];
		GLES20.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, m_iTextureID );
		GLES20.glTexParameterf( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR );
		GLES20.glTexParameterf( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR );
		GLES20.glTexParameterf( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE );
		GLES20.glTexParameterf( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE );
		GLES20.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0 );

		// SurfaceTextureとSurfaceの作成
		m_surfacetexture = new SurfaceTexture( m_iTextureID );
		m_surfacetexture.setOnFrameAvailableListener( this );
		m_surface = new Surface( m_surfacetexture );

		synchronized( this )
		{
			m_bNewFrameAvailable = false;
		}
	}

	@Override
	public void onSurfaceChanged( GL10 gl10, int width, int height )
	{
		Log.d( TAG, "onSurfaceChanged" );
		Log.d( TAG, "Thread name = " + Thread.currentThread().getName() );    // Thread name = GLThread XXXX

		// テクスチャの大きさ
		m_iTextureWidth = width;
		m_iTextureHeight = height;
		m_surfacetexture.setDefaultBufferSize( m_iTextureWidth, m_iTextureHeight );

		// Surfaceでの描画
		drawInSurface();
	}

	@Override
	public void onDrawFrame( GL10 gl10 )
	{
		//Log.d( TAG, "onDrawFrame" );
		//Log.d( TAG, "Thread name = " + Thread.currentThread().getName() );	// Thread name = GLThread XXXX

		synchronized( this )
		{
			if( m_bNewFrameAvailable )
			{
				m_surfacetexture.updateTexImage();
				m_surfacetexture.getTransformMatrix( m_f16STMatrix );
				m_bNewFrameAvailable = false;
			}
		}

		// バッファークリア
		GLES20.glClearColor( 0.0f, 1.0f, 0.0f, 1.0f );
		GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT );

		// 使用するシェーダープログラムの指定
		GLES20.glUseProgram( m_iProgram );
		MyUtils.checkGlError( "glUseProgram" );

		// テクスチャの有効化
		GLES20.glActiveTexture( GLES20.GL_TEXTURE0 );
		GLES20.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, m_iTextureID );

		// シェーダープログラムへ頂点座標値データの転送
		m_fbVertex.position( 0 );
		GLES20.glVertexAttribPointer( m_iPositionAttributeLocation, 3, GLES20.GL_FLOAT, false, 5 * MyUtils.SIZEOF_FLOAT, m_fbVertex );
		MyUtils.checkGlError( "glVertexAttribPointer Position" );
		GLES20.glEnableVertexAttribArray( m_iPositionAttributeLocation );
		MyUtils.checkGlError( "glEnableVertexAttribArray Position" );

		// シェーダープログラムへテクスチャ座標値データの転送
		m_fbVertex.position( 3 );
		GLES20.glVertexAttribPointer( m_iTexCoordAttributeLocation, 3, GLES20.GL_FLOAT, false, 5 * MyUtils.SIZEOF_FLOAT, m_fbVertex );
		MyUtils.checkGlError( "glVertexAttribPointer TexCoord" );
		GLES20.glEnableVertexAttribArray( m_iTexCoordAttributeLocation );
		MyUtils.checkGlError( "glEnableVertexAttribArray TexCoord" );

		// シェーダープログラムへ行列データの転送
		GLES20.glUniformMatrix4fv( m_iMVPMatrixUniformLocation, 1, false, m_f16MVPMatrix, 0 );
		MyUtils.checkGlError( "glUniformMatrix4fv MVPMatrix" );
		GLES20.glUniformMatrix4fv( m_iSTMatrixUniformLocation, 1, false, m_f16STMatrix, 0 );
		MyUtils.checkGlError( "glUniformMatrix4fv STMatrix" );

		// 描画
		GLES20.glDrawArrays( GLES20.GL_TRIANGLE_STRIP, 0, 4 );
		MyUtils.checkGlError( "glDrawArrays" );
		GLES20.glFinish();
	}

	@Override
	public void onFrameAvailable( SurfaceTexture surfaceTexture )
	{
		Log.d( TAG, "onFrameAvailable" );
		Log.d( TAG, "Thread name = " + Thread.currentThread().getName() );    // Thread name = main

		synchronized( this )
		{
			m_bNewFrameAvailable = true;
		}
	}

	// Surfaceでの描画
	public void drawInSurface()
	{
		Log.d( TAG, "drawInSurface" );
		Log.d( TAG, "Thread name = " + Thread.currentThread().getName() );    // Thread name = GLThread XXXX

		Canvas canvas = m_surface.lockCanvas( m_rectTexture );

		// 全体塗りつぶし
		canvas.drawColor( Color.rgb( 128, 128, 128 ) );

		// 矩形
		m_paint.setColor( Color.rgb( m_random.nextInt( 255 ), m_random.nextInt( 255 ), m_random.nextInt( 255 ) ) );
		m_paint.setStyle( Paint.Style.FILL );
		int iX          = m_random.nextInt( m_iTextureWidth );
		int iY          = m_random.nextInt( m_iTextureWidth );
		int iHalfWidth  = m_random.nextInt( 200 );
		int iHalfHeight = m_random.nextInt( 200 );
		canvas.drawRect( iX - iHalfWidth, iY - iHalfHeight, iX + iHalfWidth, iY + iHalfHeight, m_paint );

		// 円
		m_paint.setColor( Color.rgb( m_random.nextInt( 255 ), m_random.nextInt( 255 ), m_random.nextInt( 255 ) ) );
		m_paint.setStyle( Paint.Style.STROKE );
		m_paint.setStrokeWidth( 30 );
		int iRadius = m_random.nextInt( 200 );
		canvas.drawCircle( m_random.nextInt( m_iTextureWidth ), m_random.nextInt( m_iTextureHeight ), iRadius, m_paint );

		m_surface.unlockCanvasAndPost( canvas );

		m_handler.postDelayed( new Runnable()
		{
			@Override
			public void run()
			{
				drawInSurface();
			}
		}, 100 );
	}
}

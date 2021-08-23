package com.hiramine.surfacetexturerenderertest;

import androidx.appcompat.app.AppCompatActivity;

import android.opengl.GLSurfaceView;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity
{
	private GLSurfaceView          m_glsurfaceview;
	private SurfaceTextureRenderer m_surfacetexturerenderer;

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setContentView( R.layout.activity_main );

		// GLSurfaceViewの取得
		m_glsurfaceview = (GLSurfaceView)findViewById( R.id.glsurfaceview );

		// GLESバージョンとしてGLES 2.0 を指定
		m_glsurfaceview.setEGLContextClientVersion( 2 );

		// Rendererの作成
		m_surfacetexturerenderer = new SurfaceTextureRenderer();

		// Rendererの作成と、GLSurfaceViewへのセット
		m_glsurfaceview.setRenderer( m_surfacetexturerenderer );

		//  絶え間ないレンダリング
		m_glsurfaceview.setRenderMode( GLSurfaceView.RENDERMODE_CONTINUOUSLY );
	}

	// 初回表示時、および、ポーズからの復帰時
	@Override
	protected void onResume()
	{
		super.onResume();

		m_glsurfaceview.onResume();

		m_surfacetexturerenderer.startDrawInSurface();
	}

	// 別のアクティビティ（か別のアプリ）に移行したことで、バックグラウンドに追いやられた時
	@Override
	protected void onPause()
	{
		m_surfacetexturerenderer.stopDrawInSurface();

		m_glsurfaceview.onPause();

		super.onPause();
	}
}

package com.google.ar.core.examples.java.backrest;

/**##############################################
 ########### I M P O R T S ######################
 ################################################*/

import android.content.DialogInterface;
import android.content.res.Resources;
import android.media.Image;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Config.InstantPlacementMode;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.InstantPlacementPoint;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DepthSettings;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.InstantPlacementSettings;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TapHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.samplerender.Framebuffer;
import com.google.ar.core.examples.java.common.samplerender.GLError;
import com.google.ar.core.examples.java.common.samplerender.Mesh;
import com.google.ar.core.examples.java.common.samplerender.SampleRender;
import com.google.ar.core.examples.java.common.samplerender.Shader;
import com.google.ar.core.examples.java.common.samplerender.Texture;
import com.google.ar.core.examples.java.common.samplerender.VertexBuffer;
import com.google.ar.core.examples.java.common.samplerender.arcore.BackgroundRenderer;
import com.google.ar.core.examples.java.common.samplerender.arcore.PlaneRenderer;
import com.google.ar.core.examples.java.common.samplerender.arcore.SpecularCubemapFilter;
import com.google.ar.core.examples.java.helloar.R;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**##############################################
 ########### M A I N  C L A S S #################
 ################################################*/
public class HelloArActivity extends AppCompatActivity implements SampleRender.Renderer {

  /**##############################################
    ### V A R I A B L E S  &  C O N S T A N T S ###
    ################################################*/

  int planet=0;

  /** <<< Variable that gets the simple name of the class for debugging purposes >>> */
  private static final String TAG = HelloArActivity.class.getSimpleName();

  /** <<< String that displays message while app is looking for valid surfaces >>> */
  private static final String SEARCHING_PLANE_MESSAGE = "Searching for surfaces...";
  private static final String WAITING_FOR_TAP_MESSAGE = "Tap on a surface to place an object.";

  /** <<< Array of constants needed for mathematical formulas needed to create surface of spheres >>> */
  private static final float[] sphericalHarmonicFactors = {
    0.282095f,
    -0.325735f,
    0.325735f,
    -0.325735f,
    0.273137f,
    -0.273137f,
    0.078848f,
    -0.273137f,
    0.136569f,
  };

  /** <<< Floats representing nearest and farthest object placing distance >>> */
  private static final float Z_NEAR = 0.1f;
  private static final float Z_FAR = 100f;

  /** <<< Cubemap relevant values >>> */
  private static final int CUBEMAP_RESOLUTION = 16;
  private static final int CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32;

  /** <<< Variables needed for renders,sessions and the overall functionality of the app >>> */
  private GLSurfaceView surfaceView;

  private boolean installRequested;

  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
  private TapHelper tapHelper;
  private SampleRender render;

  private PlaneRenderer planeRenderer;
  private BackgroundRenderer backgroundRenderer;
  private Framebuffer virtualSceneFramebuffer;
  private boolean hasSetTextureNames = false;

  private final DepthSettings depthSettings = new DepthSettings();
  private boolean[] depthSettingsMenuDialogCheckboxes = new boolean[2];

  private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();
  private boolean[] instantPlacementSettingsMenuDialogCheckboxes = new boolean[1];


  /** <<< Distance (approximate) between camera and object used to help with object scaling >>> */
  private static final float APPROXIMATE_DISTANCE_METERS = 2.0f;

  /** <<< Variables used for point cloud to find the available surface >>> */
  private VertexBuffer pointCloudVertexBuffer;
  private Mesh pointCloudMesh;
  private Shader pointCloudShader;

  /** <<< Remembering last surface point cloud stamp for efficiency reasons >>> */
  private long lastPointCloudTimestamp = 0;

  /** <<< 3D object variables >>> */

  /** <<< This variable represents a 3D model of the virtual object, made up of vertices, edges, and faces. >>> */
  private Mesh virtualObjectMesh;

  /** <<<  This variable represents the program used to render the virtual object. The shader determines the color, lighting, and other visual properties of the object >>> */
  private Shader virtualObjectShader;

  /** <<< This variable represents a texture used to apply color and visual details to the virtual object >>> */
  private Texture virtualObjectAlbedoTexture;
  private Texture virtualMarsAlbedoTexture;
  private Texture virtualJupiterAlbedoTexture;
  private Texture virtualNeptunAlbedoTexture;

  /** <<< This variable represents a texture used specifically for instant placement of the virtual object >>> */
  private Texture virtualObjectAlbedoInstantPlacementTexture;

  /** <<< Variable used for objects that track the 3D model in real life parameters >>> */
  private final List<WrappedAnchor> wrappedAnchors = new ArrayList<>();

  /** <<< Variables for HDR lightning >>> */
  private Texture dfgTexture;
  private SpecularCubemapFilter cubemapFilter;

  /** <<< Temporary matrix used for efficiency reasons >>> */
  private final float[] modelMatrix = new float[16];
  private final float[] viewMatrix = new float[16];
  private final float[] projectionMatrix = new float[16];

  /** <<< Variables used to store the result of multiplying the modelMatrix and viewMatrix together >>> */
  private final float[] modelViewMatrix = new float[16];

  /** <<< Variables used to store the result of multiplying the modelMatrix, viewMatrix, and projectionMatrix together >>> */
  private final float[] modelViewProjectionMatrix = new float[16];
  private final float[] sphericalHarmonicsCoefficients = new float[9 * 3];
  private final float[] viewInverseMatrix = new float[16];
  private final float[] worldLightDirection = {0.0f, 0.0f, 0.0f, 0.0f};

  /** <<< Variables used to store the result of multiplying the viewMatrix and worldLightDirection together >>> */
  private final float[] viewLightDirection = new float[4];

  /**################################################
   ########## M E T H O D S #########################
   #################################################*/

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    /** <<< Sets the layout of the activity to activity_main.xml >>> */
    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);
    /** <<< Creates a new DisplayRotationHelper object and assigns it to the displayRotationHelper variable. >>> */
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

    /** <<< Creates a new TapHelper object and assigns it to the tapHelper variable. >>> */
    tapHelper = new TapHelper(/*context=*/ this);
    /** <<< Sets the OnTouchListener for the surfaceView object to tapHelper, which will handle touch events on the surface view. >>> */
    surfaceView.setOnTouchListener(tapHelper);

    render = new SampleRender(surfaceView, this, getAssets());

    installRequested = false;

    depthSettings.onCreate(this);
    instantPlacementSettings.onCreate(this);
    /** <<< Finds the ImageButton object with the ID settings_button from the layout and assigns it to the settingsButton variable. >>> */
    ImageButton settingsButton = findViewById(R.id.settings_button);
    settingsButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            PopupMenu popup = new PopupMenu(HelloArActivity.this, v);
            /** <<< Sets the OnMenuItemClickListener for the PopupMenu object to the settingsMenuClick method in the current activity. >>> */
            popup.setOnMenuItemClickListener(HelloArActivity.this::settingsMenuClick);
            popup.inflate(R.menu.settings_menu);
            popup.show();
          }
        });
  }

  /** <<< This method handles the selection of items in a popup menu, launching a specific dialog depending on the selected item >>> */
  protected boolean settingsMenuClick(MenuItem item) {
    if (item.getItemId() == R.id.depth_settings) {
      launchDepthSettingsMenuDialog();
      return true;
    } else if (item.getItemId() == R.id.instant_placement_settings) {
      launchInstantPlacementSettingsMenuDialog();
      return true;
    }
    return false;
  }


  /** <<< The purpose of this function is to perform cleanup operations before the Activity is destroyed, to release any resources that were allocated by an ARCore Session >>> */
  @Override
  protected void onDestroy() {
    if (session != null) {
      session.close();
      session = null;
    }

    super.onDestroy();
  }


  /** <<< The purpose of this function is to perform initialization and configuration operations necessary for the ARCore Session to work properly >>> */
  /** <<< Checking whether the ARCore app is installed on the device, requesting camera permissions if necessary, and handling various exceptions that may occur during the initialization process >>> */
  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        session = new Session(/* context= */ this);
      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "This device does not support AR";
        exception = e;
      } catch (Exception e) {
        message = "Failed to create AR session";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
    }

    try {
      configureSession();
      session.resume();
    } catch (CameraNotAvailableException e) {
      messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
      session = null;
      return;
    }

    surfaceView.onResume();
    displayRotationHelper.onResume();
  }


  /** <<< The purpose of this method is to pause the AR session and related components of the application when the user navigates away from the app or when the app is no longer in the foreground >>> */
  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }


  /** <<< When the Android system will display a dialog box asking the user for camera permission,once the user has responded to the dialog, this method is called. >>> */
  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }


  /** <<< This method is called by the Android system when the focus of the window changes >>> */
  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }


  /** <<< This method is called when the OpenGL surface is created, and it initializes various objects and resources required for rendering >>> */
  @Override
  public void onSurfaceCreated(SampleRender render) {
    try {
      planeRenderer = new PlaneRenderer(render);
      backgroundRenderer = new BackgroundRenderer(render);
      virtualSceneFramebuffer = new Framebuffer(render, /*width=*/ 1, /*height=*/ 1);

      cubemapFilter =
          new SpecularCubemapFilter(
              render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES);
      /** <<< Load Distant Field of Gamma lookup table for environmental lighting >>> */
      dfgTexture =
          new Texture(
              render,
              Texture.Target.TEXTURE_2D,
              Texture.WrapMode.CLAMP_TO_EDGE,
              /*useMipmaps=*/ false);
      /** <<< The dfg.raw file is a raw half-float texture with two channels. >>> */
      final int dfgResolution = 64;
      final int dfgChannels = 2;
      final int halfFloatSize = 2;

      ByteBuffer buffer =
          ByteBuffer.allocateDirect(dfgResolution * dfgResolution * dfgChannels * halfFloatSize);
      try (InputStream is = getAssets().open("models/dfg.raw")) {
        is.read(buffer.array());
      }
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.getTextureId());
      GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture");
      GLES30.glTexImage2D(
          GLES30.GL_TEXTURE_2D,
          /*level=*/ 0,
          GLES30.GL_RG16F,
          /*width=*/ dfgResolution,
          /*height=*/ dfgResolution,
          /*border=*/ 0,
          GLES30.GL_RG,
          GLES30.GL_HALF_FLOAT,
          buffer);
      GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D");

      /** <<< point cloud >>> */
      pointCloudShader =
          Shader.createFromAssets(
                  render, "shaders/point_cloud.vert", "shaders/point_cloud.frag", /*defines=*/ null)
              .setVec4(
                  "u_Color", new float[] {31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f})
              .setFloat("u_PointSize", 5.0f);
      /** <<< four entries per vertex: X, Y, Z, confidence >>> */
      pointCloudVertexBuffer =
          new VertexBuffer(render, /*numberOfEntriesPerVertex=*/ 4, /*entries=*/ null);
      final VertexBuffer[] pointCloudVertexBuffers = {pointCloudVertexBuffer};
      pointCloudMesh =
          new Mesh(
              render, Mesh.PrimitiveMode.POINTS, /*indexBuffer=*/ null, pointCloudVertexBuffers);

      /** <<< Virtual object to render (ARCore pawn) >>> */
      virtualObjectAlbedoTexture =
          Texture.createFromAsset(
              render,
              "models/pawn_albedo.png",
              Texture.WrapMode.CLAMP_TO_EDGE,
              Texture.ColorFormat.SRGB);
      virtualMarsAlbedoTexture =
              Texture.createFromAsset(
                      render,
                      "mars/pawn_albedo.png",
                      Texture.WrapMode.CLAMP_TO_EDGE,
                      Texture.ColorFormat.SRGB);
      virtualJupiterAlbedoTexture =
              Texture.createFromAsset(
                      render,
                      "jupiter/pawn_albedo.png",
                      Texture.WrapMode.CLAMP_TO_EDGE,
                      Texture.ColorFormat.SRGB);
      virtualNeptunAlbedoTexture =
              Texture.createFromAsset(
                      render,
                      "neptun/pawn_albedo.png",
                      Texture.WrapMode.CLAMP_TO_EDGE,
                      Texture.ColorFormat.SRGB);
      virtualObjectAlbedoInstantPlacementTexture =
          Texture.createFromAsset(
              render,
              "models/pawn_albedo_instant_placement.png",
              Texture.WrapMode.CLAMP_TO_EDGE,
              Texture.ColorFormat.SRGB);
      Texture virtualObjectPbrTexture =
          Texture.createFromAsset(
              render,
              "models/pawn_roughness_metallic_ao.png",
              Texture.WrapMode.CLAMP_TO_EDGE,
              Texture.ColorFormat.LINEAR);

      virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj");
      virtualObjectShader =
          Shader.createFromAssets(
                  render,
                  "shaders/environmental_hdr.vert",
                  "shaders/environmental_hdr.frag",
                  /*defines=*/ new HashMap<String, String>() {
                    {
                      put(
                          "NUMBER_OF_MIPMAP_LEVELS",
                          Integer.toString(cubemapFilter.getNumberOfMipmapLevels()));
                    }
                  })
              .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
              .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture)
              .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
              .setTexture("u_DfgTexture", dfgTexture);
    } catch (IOException e) {
      Log.e(TAG, "Failed to read a required asset file", e);
      messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
    }
  }


  /** <<< This method is called by the Android framework when the surface size changes, for example when the device is rotated or when the app is put into multi-window mode >>> */
  @Override
  public void onSurfaceChanged(SampleRender render, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    virtualSceneFramebuffer.resize(width, height);
  }

  @Override
  public void onDrawFrame(SampleRender render) {
    if (session == null) {
      return;
    }

    // [TBD]Texture names should only be set once on a GL thread unless they change. This is done during
    //[TBD] onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
    // initialized during the execution of onSurfaceCreated.[TBD]
    if (!hasSetTextureNames) {
      session.setCameraTextureNames(
          new int[] {backgroundRenderer.getCameraColorTexture().getTextureId()});
      hasSetTextureNames = true;
    }

    // -- Update per-frame state [TBD]

    // Notify ARCore session that the view size changed so that the perspective matrix and[TBD]
    // the video background can be properly adjusted.[TBD]
    displayRotationHelper.updateSessionIfNeeded(session);

    // Obtain the current frame from the AR Session. When the configuration is set to[TBD]
    // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the[TBD]
    // camera framerate.[TBD]
    Frame frame;
    try {
      frame = session.update();
    } catch (CameraNotAvailableException e) {
      Log.e(TAG, "Camera not available during onDrawFrame", e);
      messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
      return;
    }
    Camera camera = frame.getCamera();

    // Update BackgroundRenderer state to match the depth settings. [TBD]
    try {
      backgroundRenderer.setUseDepthVisualization(
          render, depthSettings.depthColorVisualizationEnabled());
      backgroundRenderer.setUseOcclusion(render, depthSettings.useDepthForOcclusion());
    } catch (IOException e) {
      Log.e(TAG, "Failed to read a required asset file", e);
      messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
      return;
    }
    // [TBD]BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
    //[TBD] used to draw the background camera image.
    backgroundRenderer.updateDisplayGeometry(frame);

    if (camera.getTrackingState() == TrackingState.TRACKING
        && (depthSettings.useDepthForOcclusion()
            || depthSettings.depthColorVisualizationEnabled())) {
      try (Image depthImage = frame.acquireDepthImage16Bits()) {
        backgroundRenderer.updateCameraDepthTexture(depthImage);
      } catch (NotYetAvailableException e) {
        // [TBD]This normally means that depth data is not available yet. This is normal so we will not
        // spam the logcat with this.[TBD]
      }
    }

    // Handle one tap per frame.[TBD]
    handleTap(frame, camera);

    // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.[TBD]
    trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

    // [TBD]Show a message based on whether tracking has failed, if planes are detected, and if the user
    // has placed any objects.[TBD]
    String message = null;
    if (camera.getTrackingState() == TrackingState.PAUSED) {
      if (camera.getTrackingFailureReason() == TrackingFailureReason.NONE) {
        message = SEARCHING_PLANE_MESSAGE;
      } else {
        message = TrackingStateHelper.getTrackingFailureReasonString(camera);
      }
    } else if (hasTrackingPlane()) {
      if (wrappedAnchors.isEmpty()) {
        message = WAITING_FOR_TAP_MESSAGE;
      }
    } else {
      message = SEARCHING_PLANE_MESSAGE;
    }
    if (message == null) {
      messageSnackbarHelper.hide(this);
    } else {
      messageSnackbarHelper.showMessage(this, message);
    }

    // -- Draw background [TBD]

    if (frame.getTimestamp() != 0) {
      // [TBD] Suppress rendering if the camera did not produce the first frame yet. This is to avoid
      // [TBD] drawing possible leftover data from previous sessions if the texture is reused.
      backgroundRenderer.drawBackground(render);
    }

    // [TBD] If not tracking, don't draw 3D objects.
    if (camera.getTrackingState() == TrackingState.PAUSED) {
      return;
    }

    // [TBD] -- Draw non-occluded virtual objects (planes, point cloud)

    //[TBD]  Get projection matrix.
    camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);

    //[TBD] Get camera matrix and draw.
    camera.getViewMatrix(viewMatrix, 0);

    //[TBD] Visualize tracked points.
    //[TBD] Use try-with-resources to automatically release the point cloud.
    try (PointCloud pointCloud = frame.acquirePointCloud()) {
      if (pointCloud.getTimestamp() > lastPointCloudTimestamp) {
        pointCloudVertexBuffer.set(pointCloud.getPoints());
        lastPointCloudTimestamp = pointCloud.getTimestamp();
      }
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
      pointCloudShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
      render.draw(pointCloudMesh, pointCloudShader);
    }

    // Visualize planes.[TBD]
    planeRenderer.drawPlanes(
        render,
        session.getAllTrackables(Plane.class),
        camera.getDisplayOrientedPose(),
        projectionMatrix);

    // -- Draw occluded virtual objects [TBD]

    // Update lighting parameters in the shader [TBD]
    updateLightEstimation(frame.getLightEstimate(), viewMatrix);

    // Visualize anchors created by touch. [TBD]
    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);
    for (WrappedAnchor wrappedAnchor : wrappedAnchors) {
      Anchor anchor = wrappedAnchor.getAnchor();
      Trackable trackable = wrappedAnchor.getTrackable();
      if (anchor.getTrackingState() != TrackingState.TRACKING) {
        continue;
      }

      // Get the current pose of an Anchor in world space. The Anchor pose is updated [TBD]
      // during calls to session.update() as ARCore refines its estimate of the world. [TBD]
      anchor.getPose().toMatrix(modelMatrix, 0);

      // Calculate model/view/projection matrices [TBD]
      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

      // Update shader properties and draw [TBD]
      virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
      virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

      if (trackable instanceof InstantPlacementPoint
          && ((InstantPlacementPoint) trackable).getTrackingMethod()
              == InstantPlacementPoint.TrackingMethod.SCREENSPACE_WITH_APPROXIMATE_DISTANCE) {
        virtualObjectShader.setTexture(
            "u_AlbedoTexture", virtualObjectAlbedoInstantPlacementTexture);
      } else {
        if(planet==0){
          virtualObjectShader.setTexture("u_AlbedoTexture", virtualMarsAlbedoTexture);
          planet=1;
        }
        else if(planet==1){
          virtualObjectShader.setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture);
          planet=2;
        }
        else if(planet==2){
          virtualObjectShader.setTexture("u_AlbedoTexture", virtualJupiterAlbedoTexture);
          planet=3;
        }
        else{
          virtualObjectShader.setTexture("u_AlbedoTexture", virtualNeptunAlbedoTexture);
          planet=0;
        }
        render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
      }
    }

    // Compose the virtual scene with the background. [TBD]
    backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
  }

  // Handle only one tap per frame, as taps are usually low frequency compared to frame rate. [TBD]
  private void handleTap(Frame frame, Camera camera) {
    MotionEvent tap = tapHelper.poll();
    if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
      List<HitResult> hitResultList;
      if (instantPlacementSettings.isInstantPlacementEnabled()) {
        hitResultList =
            frame.hitTestInstantPlacement(tap.getX(), tap.getY(), APPROXIMATE_DISTANCE_METERS);
      } else {
        hitResultList = frame.hitTest(tap);
      }
      for (HitResult hit : hitResultList) {
        // If any plane, Oriented Point, or Instant Placement Point was hit, create an anchor. [TBD]
        Trackable trackable = hit.getTrackable();
        // If a plane was hit, check that it was hit inside the plane polygon. [TBD]
        // DepthPoints are only returned if Config.DepthMode is set to AUTOMATIC. [TBD]
        if ((trackable instanceof Plane
                && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
            || (trackable instanceof Point
                && ((Point) trackable).getOrientationMode()
                    == OrientationMode.ESTIMATED_SURFACE_NORMAL)
            || (trackable instanceof InstantPlacementPoint)
            || (trackable instanceof DepthPoint)) {
          // Cap the number of objects created. This avoids overloading both the [TBD]
          // rendering system and ARCore. [TBD]
          if (wrappedAnchors.size() >= 20) {
            wrappedAnchors.get(0).getAnchor().detach();
            wrappedAnchors.remove(0);
          }

          // [TBD]Adding an Anchor tells ARCore that it should track this position in
          // space. This anchor is created on the Plane to place the 3D model
          // in the correct position relative both to the world and to the plane.[TBD]
          wrappedAnchors.add(new WrappedAnchor(hit.createAnchor(), trackable));
          // [TBD]For devices that support the Depth API, shows a dialog to suggest enabling
          // depth-based occlusion. This dialog needs to be spawned on the UI thread.[TBD]
          this.runOnUiThread(this::showOcclusionDialogIfNeeded);

          // [TBD]Hits are sorted by depth. Consider only closest hit on a plane, Oriented Point, or
          // Instant Placement Point.[TBD]
          break;
        }
      }
    }
  }

  /**[TBD]
   * Shows a pop-up dialog on the first call, determining whether the user wants to enable
   * depth-based occlusion. The result of this dialog can be retrieved with useDepthForOcclusion().
   [TBD]*/
  private void showOcclusionDialogIfNeeded() {
    boolean isDepthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
    if (!depthSettings.shouldShowDepthEnableDialog() || !isDepthSupported) {
      return; // Don't need to show dialog.[TBD]
    }

    // Asks the user whether they want to use depth-based occlusion. [TBD]
    new AlertDialog.Builder(this)
        .setTitle(R.string.options_title_with_depth)
        .setMessage(R.string.depth_use_explanation)
        .setPositiveButton(
            R.string.button_text_enable_depth,
            (DialogInterface dialog, int which) -> {
              depthSettings.setUseDepthForOcclusion(true);
            })
        .setNegativeButton(
            R.string.button_text_disable_depth,
            (DialogInterface dialog, int which) -> {
              depthSettings.setUseDepthForOcclusion(false);
            })
        .show();
  }

  private void launchInstantPlacementSettingsMenuDialog() {
    resetSettingsMenuDialogCheckboxes();
    Resources resources = getResources();
    new AlertDialog.Builder(this)
        .setTitle(R.string.options_title_instant_placement)
        .setMultiChoiceItems(
            resources.getStringArray(R.array.instant_placement_options_array),
            instantPlacementSettingsMenuDialogCheckboxes,
            (DialogInterface dialog, int which, boolean isChecked) ->
                instantPlacementSettingsMenuDialogCheckboxes[which] = isChecked)
        .setPositiveButton(
            R.string.done,
            (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
        .setNegativeButton(
            android.R.string.cancel,
            (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
        .show();
  }

  /** Shows checkboxes to the user to facilitate toggling of depth-based effects. [TBD]*/
  private void launchDepthSettingsMenuDialog() {
    // Retrieves the current settings to show in the checkboxes. [TBD]
    resetSettingsMenuDialogCheckboxes();

    // Shows the dialog to the user. [TBD]
    Resources resources = getResources();
    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
      // With depth support, the user can select visualization options. [TBD]
      new AlertDialog.Builder(this)
          .setTitle(R.string.options_title_with_depth)
          .setMultiChoiceItems(
              resources.getStringArray(R.array.depth_options_array),
              depthSettingsMenuDialogCheckboxes,
              (DialogInterface dialog, int which, boolean isChecked) ->
                  depthSettingsMenuDialogCheckboxes[which] = isChecked)
          .setPositiveButton(
              R.string.done,
              (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
          .setNegativeButton(
              android.R.string.cancel,
              (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
          .show();
    } else {
      // Without depth support, no settings are available. [TBD]
      new AlertDialog.Builder(this)
          .setTitle(R.string.options_title_without_depth)
          .setPositiveButton(
              R.string.done,
              (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
          .show();
    }
  }

  private void applySettingsMenuDialogCheckboxes() {
    depthSettings.setUseDepthForOcclusion(depthSettingsMenuDialogCheckboxes[0]);
    depthSettings.setDepthColorVisualizationEnabled(depthSettingsMenuDialogCheckboxes[1]);
    instantPlacementSettings.setInstantPlacementEnabled(
        instantPlacementSettingsMenuDialogCheckboxes[0]);
    configureSession();
  }

  private void resetSettingsMenuDialogCheckboxes() {
    depthSettingsMenuDialogCheckboxes[0] = depthSettings.useDepthForOcclusion();
    depthSettingsMenuDialogCheckboxes[1] = depthSettings.depthColorVisualizationEnabled();
    instantPlacementSettingsMenuDialogCheckboxes[0] =
        instantPlacementSettings.isInstantPlacementEnabled();
  }

  /** Checks if we detected at least one plane.[TBD] */
  private boolean hasTrackingPlane() {
    for (Plane plane : session.getAllTrackables(Plane.class)) {
      if (plane.getTrackingState() == TrackingState.TRACKING) {
        return true;
      }
    }
    return false;
  }

  /** Update state based on the current frame's light estimation.[TBD] */
  private void updateLightEstimation(LightEstimate lightEstimate, float[] viewMatrix) {
    if (lightEstimate.getState() != LightEstimate.State.VALID) {
      virtualObjectShader.setBool("u_LightEstimateIsValid", false);
      return;
    }
    virtualObjectShader.setBool("u_LightEstimateIsValid", true);

    Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0);
    virtualObjectShader.setMat4("u_ViewInverse", viewInverseMatrix);

    updateMainLight(
        lightEstimate.getEnvironmentalHdrMainLightDirection(),
        lightEstimate.getEnvironmentalHdrMainLightIntensity(),
        viewMatrix);
    updateSphericalHarmonicsCoefficients(
        lightEstimate.getEnvironmentalHdrAmbientSphericalHarmonics());
    cubemapFilter.update(lightEstimate.acquireEnvironmentalHdrCubeMap());
  }

  private void updateMainLight(float[] direction, float[] intensity, float[] viewMatrix) {
    //[TBD] We need the direction in a vec4 with 0.0 as the final component to transform it to view space
    worldLightDirection[0] = direction[0];
    worldLightDirection[1] = direction[1];
    worldLightDirection[2] = direction[2];
    Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0);
    virtualObjectShader.setVec4("u_ViewLightDirection", viewLightDirection);
    virtualObjectShader.setVec3("u_LightIntensity", intensity);
  }

  private void updateSphericalHarmonicsCoefficients(float[] coefficients) {
    // Pre-multiply the spherical harmonics coefficients before passing them to the shader. The
    // constants in sphericalHarmonicFactors were derived from three terms:
    //[TBD]
    // 1. The normalized spherical harmonics basis functions (y_lm)
    //[TBD]
    // 2. The lambertian diffuse BRDF factor (1/pi)
    //[TBD]
    // 3. A <cos> convolution. This is done to so that the resulting function outputs the irradiance
    // of all incoming light over a hemisphere for a given surface normal, which is what the shader
    // (environmental_hdr.frag) expects.
    //[TBD]
    // You can read more details about the math here:
    // https://google.github.io/filament/Filament.html#annex/sphericalharmonics

    if (coefficients.length != 9 * 3) {
      throw new IllegalArgumentException(
          "The given coefficients array must be of length 27 (3 components per 9 coefficients");
    }

    // Apply each factor to every component of each coefficient [TBD]
    for (int i = 0; i < 9 * 3; ++i) {
      sphericalHarmonicsCoefficients[i] = coefficients[i] * sphericalHarmonicFactors[i / 3];
    }
    virtualObjectShader.setVec3Array(
        "u_SphericalHarmonicsCoefficients", sphericalHarmonicsCoefficients);
  }

  /** Configures the session with feature settings. [TBD] */
  private void configureSession() {
    Config config = session.getConfig();
    config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
      config.setDepthMode(Config.DepthMode.AUTOMATIC);
    } else {
      config.setDepthMode(Config.DepthMode.DISABLED);
    }
    if (instantPlacementSettings.isInstantPlacementEnabled()) {
      config.setInstantPlacementMode(InstantPlacementMode.LOCAL_Y_UP);
    } else {
      config.setInstantPlacementMode(InstantPlacementMode.DISABLED);
    }
    session.configure(config);
  }
}

/**
 * [TBD]Associates an Anchor with the trackable it was attached to. This is used to be able to check
 * whether or not an Anchor originally was attached to an {@link InstantPlacementPoint}.[TBD]
 */

/**##############################################
 ########### O T H E R  C L A S S E S ###########
 ################################################*/

class WrappedAnchor {
  private Anchor anchor;
  private Trackable trackable;

  public WrappedAnchor(Anchor anchor, Trackable trackable) {
    this.anchor = anchor;
    this.trackable = trackable;
  }

  public Anchor getAnchor() {
    return anchor;
  }

  public Trackable getTrackable() {
    return trackable;
  }
}

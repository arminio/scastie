package com.olegych.scastie
package client

import api._
import japgolly.scalajs.react._
import vdom.all._
import org.scalajs.dom
import org.scalajs.dom.window._
import org.scalajs.dom.raw.HTMLScriptElement

object App {

  private def setTitle(state: AppState) =
    if (state.inputsHasChanged) {
      Callback(dom.document.title = "Scastie (*)")
    } else {
      Callback(dom.document.title = "Scastie")
    }

  val component =
    ScalaComponent.builder[AppProps]("App")
//    ScalaComponent.builder[AppProps]("App")
      .initialState[AppState](LocalStorage.load.getOrElse(AppState.default))
      .backend(new AppBackend(_))
      .renderPS {
        case (scope, props, state) => {
          val theme =
            if (state.isDarkTheme) "dark"
            else "light"

          val sideBar =
            if (!props.isEmbedded)
              TagMod(SideBar(state, scope.backend))
            else EmptyVdom

          val appClass =
            if (!props.isEmbedded) "app"
            else "app embedded"

          val desktop =
            if (state.dimensions.forcedDesktop) "force-desktop"
            else ""

          def appStyle: TagMod =
            Seq(height := s"${innerHeight}px", width := s"${innerWidth}px").toTagMod

          div(`class` := s"$appClass $theme $desktop", appStyle)(
            sideBar,
            MainPanel(state, scope.backend, props),
            Welcome(state, scope.backend),
            Help(state, scope.backend),
            Share(props.router, state, scope.backend),
            Reset(state, scope.backend)
          )
        }
      }
      .componentWillMount { current =>
        current.backend.start(current.props) >> setTitle(current.state)
      }
      .componentDidMount(_.backend.setDimensions())
      .componentDidUpdate { v =>
        val state = v.prevState
        val backend = v.$.backend
        val scope = v.$
        val direct = scope.accessDirect

        val setDimensions =
          if (direct.state.dimensions.dimensionsHaveChanged) {
            backend.setDimensions()

          } else Callback(())

        val scalaJsRunId = "scastie-scalajs-playground-run"

        def createScript(id: String): HTMLScriptElement = {
          val newScript = dom.document
            .createElement("script")
            .asInstanceOf[HTMLScriptElement]
          newScript.id = id
          dom.document.body.appendChild(newScript)
          newScript
        }

        def removeIfExist(id: String): Unit = {
          Option(dom.document.getElementById(id))
            .foreach(element => element.parentNode.removeChild(element))
        }

        def runScalaJs(): Unit = {
          removeIfExist(scalaJsRunId)
          val scalaJsRunScriptElement = createScript(scalaJsRunId)
          println("== Running Scala.js ==")
          scalaJsRunScriptElement.innerHTML =
            """|try {
               |  var main = new Main()
               |  com.olegych.scastie.client.ClientMain().signal(
               |    main.result,
               |    main.attachedElements
               |  )
               |} catch (e) {
               | console.log("== Caught JS Error ==")
               | console.log(e)
               |}""".stripMargin
        }

        val executeScalaJs =
          if (direct.state.loadScalaJsScript &&
              !direct.state.isScalaJsScriptLoaded &&
              direct.state.snippetIdIsScalaJS &&
              direct.state.snippetId.nonEmpty &&
              !direct.state.isRunning) {

            direct.modState(
              _.setLoadScalaJsScript(false).scalaJsScriptLoaded
                .setRunning(true)
            )

            Callback {
              val scalaJsId = "scastie-scalajs-playground-target"

              def scalaJsUrl(snippetId: SnippetId): String = {
                val middle =
                  snippetId match {
                    case SnippetId(uuid, None) => uuid
                    case SnippetId(uuid,
                                   Some(SnippetUserPart(login, update))) =>
                      s"$login/$uuid/${update.getOrElse(0)}"
                  }

                s"/${Shared.scalaJsHttpPathPrefix}/$middle/${ScalaTarget.Js.targetFilename}"
              }

              println("== Loading Scala.js ==")

              removeIfExist(scalaJsId)
              val scalaJsScriptElement = createScript(scalaJsId)
              scalaJsScriptElement.onload = { (e: dom.Event) =>
                runScalaJs()
              }
              if (state.snippetId.nonEmpty) {
                scalaJsScriptElement.src = scalaJsUrl(state.snippetId.get)
              } else {
                println("empty snippetId")
              }
            }
          } else Callback(())

        val reRunScalaJs =
          if (direct.state.isReRunningScalaJs) {
            Callback(runScalaJs()) >>
              scope.modState(_.setIsReRunningScalaJs(false))
          } else Callback(())

        setTitle(state) >> setDimensions >> executeScalaJs >> reRunScalaJs
      }
      .componentWillReceiveProps { v =>
        val next = v.nextProps.snippetId
        val current = v.currentProps.snippetId
        val state = v.$.state
        val backend = v.$.backend

        val loadSnippet =
          if (next != current) {
            next match {
              case Some(snippetId) =>
                backend.loadSnippet(snippetId) >>
                  backend.setView(View.Editor)
              case _ => Callback(())
            }

          } else Callback(())

        setTitle(state) >> loadSnippet
      }
      .build

  def apply(props: AppProps) = component(props)
}

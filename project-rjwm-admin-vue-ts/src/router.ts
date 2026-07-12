import Vue from "vue";
import Router from "vue-router";
import Layout from "@/layout/index.vue";

Vue.use(Router);

const router = new Router({
  scrollBehavior: (to, from, savedPosition) => {
    if (savedPosition) {
      return savedPosition;
    }
    return { x: 0, y: 0 };
  },
  base: process.env.BASE_URL,
  routes: [
    {
      path: "/login",
      component: () =>
        import(/* webpackChunkName: "login" */ "@/views/login/index.vue"),
      meta: { title: "智慧物流 IoT 平台", hidden: true, notNeedAuth: true }
    },
    {
      path: "/404",
      component: () => import(/* webpackChunkName: "404" */ "@/views/404.vue"),
      meta: { title: "智慧物流 IoT 平台", hidden: true, notNeedAuth: true }
    },
    {
      path: "/",
      component: Layout,
      redirect: "/tracking",
      children: [
        {
          path: "tracking",
          component: () =>
            import(/* webpackChunkName: "tracking" */ "@/views/logistics/Tracking.vue"),
          name: "LogisticsTracking",
          meta: {
            title: "货物追踪",
            icon: "dashboard",
            affix: true
          }
        },
        {
          path: "fleet",
          component: () =>
            import(/* webpackChunkName: "fleet" */ "@/views/logistics/Fleet.vue"),
          meta: {
            title: "车辆调度",
            icon: "icon-order"
          }
        },
        {
          path: "alerts",
          component: () =>
            import(/* webpackChunkName: "alerts" */ "@/views/logistics/Alerts.vue"),
          meta: {
            title: "告警中心",
            icon: "icon-category"
          }
        },
        {
          path: "management",
          component: () =>
            import(/* webpackChunkName: "management" */ "@/views/logistics/Management.vue"),
          meta: {
            title: "车辆货物",
            icon: "icon-dish"
          }
        },
        {
          path: "devices",
          component: () =>
            import(/* webpackChunkName: "devices" */ "@/views/logistics/Devices.vue"),
          meta: {
            title: "设备在线",
            icon: "icon-combo"
          }
        },
        {
          path: "assistant",
          component: () =>
            import(/* webpackChunkName: "assistant" */ "@/views/logistics/Assistant.vue"),
          meta: {
            title: "智能问答",
            icon: "icon-statistics"
          }
        }
      ]
    },
    {
      path: "*",
      redirect: "/404",
      meta: { hidden: true }
    }
  ]
});

export default router;

import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: "Apex",
  description: "Reaching for the sky on HyPixel SkyBlock",
  cleanUrls: true,
  markdown: {
    config: (md) => {
      //      md.use(minecraftHoverPlugin)
    }
  },
  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
    nav: [
      { text: 'Home', link: '/' },
      { text: 'For Developers', link: '/developers' }
    ],

    sidebar: [
      {
        text: 'Resource Packs',
        items: [
          { text: 'Basics', link: '/developers/texture-packs' },
          { text: 'Item Retexturing', link: '/developers/texture-packs/items' },
        ]
      }
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/FirmamentMC/Firmament' },
      { icon: 'modrinth', link: 'https://modrinth.com/mod/firmament' },
    ]
  }
})

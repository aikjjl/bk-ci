<template>
    <div class="side-nav">
        <bk-dropdown
            v-if="Object.keys(dropdownConfig).length"
            :list="dropdownConfig.list"
            :selected="dropdownConfig.selected"
            :setting-key="dropdownConfig.settingKey"
            :display-key="dropdownConfig.displayKey"
            :search-key="dropdownConfig.searchKey"
            :searchable="dropdownConfig.searchable"
            @item-selected="dropdownConfig.itemSelected">
        </bk-dropdown>
        <div class="side-menu-nav clearfix"
            v-else>
            <logo :name="nav.icon" size="32" class="nav-icon" />
            <span class="side-menu-title">{{ nav.title }}</span>
            <i v-if="nav.url" class="bk-icon icon-question-circle" @click="goToDoc(nav.url)"></i>
        </div>
        <section class="side-menu-list">
            <div class="side-menu-item"
                v-for="(menu, index) of sideMenuList" :key="index">
                <p class="title" v-if="menu.title">{{ menu.title }}</p>
                <bk-menu
                    v-if="sideMenuList.length"
                    :key="`devopsSideMenu${index}`"
                    :list="menu.list"
                    @item-selected="menuSelected">
                </bk-menu>
            </div>
        </section>
        <p class="biz-copyright">Copyright © 2012-<span>{{ currentYear }}</span> Tencent BlueKing. All Rights Reserved</p>
    </div>
</template>
<script>
    import bkMenu from '@/components/common/menu'

    export default {
        name: 'side-nav',
        components: {
            bkMenu
        },
        props: {
            sideMenuList: {
                type: Array,
                default () {
                    return [
                        {
                            list: [],
                            title: ''
                        }
                    ]
                }
            },
            dropdownConfig: {
                type: Object,
                default () {
                    return {}
                }
            },
            nav: {
                type: Object,
                default () {
                    return {
                        icon: '',
                        title: ''
                    }
                }
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            currentYear () {
                return new Date().getFullYear()
            }
        },
        methods: {
            /**
             * 左侧导航 menu 选择事件
             *
             * @param {Object} data menu 数据
             */
            menuSelected (data) {
                data.item.isSelected = true

                this.$router.push({
                    name: data.isChild ? data.child.id : data.item.id,
                    params: data.item.params
                })
            },
            goToDoc (url) {
                window.open(url, '_blank')
            }
        }
    }
</script>

<style lang="scss">
    @import './../../scss/conf';

    .side-nav {
        position: relative;
        height: 100%;
        padding-bottom: 100px;
        box-shadow:0px 2px 5px 0px rgba(51,60,72,0.03);

        .biz-pm-dropdown .bk-selector-list {
            top: 60px !important;
        }
    }

    .main-menu {
        padding-bottom: 15px;
        margin: 20px 0 15px 0;
        border-bottom: 1px solid #dde4eb;
    }
    .sub-menu {
        .title {
            font-size: 14px;
            font-weight: bold;
            text-align: left;
            color: #c3cdd7;
            padding-left: 27px;
            margin: 15px 0 15px 0;
            display: inline-block;
        }
    }
    .side-menu-list {
        padding-top: 20px;
        .title {
            margin-left: 25px;
            margin-bottom: 15px;
            font: {
                size: 14px;
                weight: bold;
            }
            color: #c3cdd7;
        }
        .side-menu-item:first-child {
            margin-bottom: 30px;
            & + .side-menu-item {
                border-top: 1px solid $borderColor;
            }
        }
        .side-menu-item + .side-menu-item {
            padding-top: 34px;
        }
    }
    .side-menu-nav {
        height: 60px;
        padding: 0 25px;
        border-bottom: 1px solid $borderWeightColor;
        font-size: 16px;
        color: #333948;
        display: flex;
        align-items: center;
        box-shadow:2px 0px 5px 0px rgba(51,60,72,0.03);
        .nav-icon {
            margin-right: 10px;
        }
        i {
            padding-left: 10px;
            color: #c4cdd6;
            cursor: pointer;
        }
    }
    .default-sidemenu-icon {
        display: inline-block;
        width: 32px;
        height: 32px;
        line-height: 32px;
        border-radius: 50%;
        color: #fff;
        text-align: center;
        font-size: 20px;
        background-color: #737987;
    }
    .biz-copyright {
        font-size: 12px;
        color: #b7c0ca;
        width: 100%;
        position: absolute;
        bottom: 25px;
        text-align: center;
        line-height: 20px;
        padding: 0 15px;
    }
</style>

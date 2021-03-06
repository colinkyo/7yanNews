package com.a7yan.news.detailpager;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.a7yan.news.R;
import com.a7yan.news.activity.NewsDetailActivity;
import com.a7yan.news.base.DetailBasePager;
import com.a7yan.news.domain.NewsCenterPagerBean;
import com.a7yan.news.domain.TabDetailPagerBean;
import com.a7yan.news.utils.CacheUtils;
import com.a7yan.news.utils.Constants;
import com.a7yan.news.utils.DensityUtil;
import com.a7yan.news.view.HorizontalScrollViewPager;
import com.alibaba.fastjson.JSON;
import com.bumptech.glide.Glide;
import com.example.refreshlistview.RefreshListView;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.StringCallback;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.Call;
import okhttp3.Request;

/**
 * Created by 7Yan on 2017/2/6.
 */

public class TabDetailPager extends DetailBasePager {

    public static final String READ_ARRAY_ID = "read_array_id";
    //@BindView(R.id.listview)
    private RefreshListView listview;
    @BindView(R.id.viewpage)
    HorizontalScrollViewPager viewpage;
    @BindView(R.id.tv_title)
    TextView tv_title;
    @BindView(R.id.ll_point_group)
    LinearLayout ll_point_group;
    private NewsCenterPagerBean.NewsCenterPagerData.ChildrenData childrenData;
    private TextView textView;
    private String url;
    private List<TabDetailPagerBean.DataBean.TopnewsBean> topnews;
    /**
     * 上次选择的位置
     */
    private int preSelectPosition;
    private List<TabDetailPagerBean.DataBean.NewsBean> news;
    private TabDetailPagerListAdapter adapter;
    private String moreurl;
    private boolean isLoadMore = false;
    private InteracHandler handler;
    private boolean isDragging;

    public TabDetailPager(Context context, NewsCenterPagerBean.NewsCenterPagerData.ChildrenData childrenData) {
        super(context);
        this.childrenData = childrenData;

    }

    @Override
    public View initView() {
        /*textView = new TextView(mContext);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(25);
        textView.setTextColor(Color.RED);
        return textView;*/
        View view = View.inflate(mContext, R.layout.tab_detail_pager, null);
        listview = (RefreshListView ) view.findViewById(R.id.listview);
        View topnewsView = View.inflate(mContext, R.layout.topnews, null);
//        使用ButterKnife绑定XML文件
        //ButterKnife.bind(this, view);
        ButterKnife.bind(this, topnewsView);
//        监听ViewPage页面的变化动态改变红点和标题
        viewpage.addOnPageChangeListener(new MyOnPageChangeListener());
//        把顶部新闻模块以头的方式加载到ListView中
//        listview.addHeaderView(topnewsView);
//        ListView自定义方法
        listview.addTopNews(topnewsView);
//        监听控件刷新
        listview.setOnRefreshListener(new MysetOnRefreshListener());
//        设置单击监听
        listview.setOnItemClickListener(new MyOnItemClickListener());
        return view;
    }
    class  MyOnItemClickListener implements AdapterView.OnItemClickListener{
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//            因为有头部，所以数据的下标需要把位置减去1
            int realPosition = position -1;
            TabDetailPagerBean.DataBean.NewsBean newsData = news.get(realPosition);
            Log.d("MyOnItemClickListener", newsData.toString());
            String readArrayId =CacheUtils.getString(mContext, READ_ARRAY_ID);
            if(!readArrayId.contains(newsData.getId()+"")){
                //保存
                //""->"35111,35112,"
                CacheUtils.putString(mContext,READ_ARRAY_ID,readArrayId+newsData.getId()+",");
                //2.刷新适配器,重新加载数据绘制视图时，可以判断是否点击过
                adapter.notifyDataSetChanged();
            }
//            打开新闻详情页面
            Intent intent = new Intent(mContext,NewsDetailActivity.class);
            intent.putExtra("url",newsData.getUrl());
            Log.d("MyOnItemClickListener", "要打开的新闻详情页面=============" + newsData.getUrl());
            mContext.startActivity(intent);
        }
    }
    class MysetOnRefreshListener implements RefreshListView.OnRefreshListener{

        @Override
        public void onPullDownlRefresh(){
            getDataFromNet();
        }

        @Override
        public void onLoadMore() {
            if(TextUtils.isEmpty(moreurl))
            {
                Toast.makeText(mContext, "没有更多数据啦", Toast.LENGTH_SHORT).show();
                listview.onRefreshFinish(false);

            }else {
                getMoreDataFromNet();
            }
        }
    }

    private void getMoreDataFromNet() {
        OkHttpUtils
                .get()
                .url(moreurl)
                .id(100)
                .build()
                .execute(new MyMoreStringCallback());
    }
    class MyMoreStringCallback extends StringCallback {
        @Override
        public void onBefore(Request request, int id) {
            super.onBefore(request, id);
            Log.d("MyStringCallback", "开始联网。。。");
        }

        @Override
        public void onAfter(int id) {
            super.onAfter(id);
            Log.d("MyStringCallback", "结束联网。。成功失败都会执行。");
        }

        @Override
        public void inProgress(float progress, long total, int id) {
            super.inProgress(progress, total, id);
            Log.d("MyStringCallback", "联网中。。。");
        }

        @Override
        public void onError(Call call, Exception e, int i) {
            e.printStackTrace();
            listview.onRefreshFinish(false);
            Log.d("MyStringCallback", "联网失败:" + e.getMessage());
        }

        @Override
        public void onResponse(String response, int id) {
            Log.d("MyStringCallback", "联网成功。。。" + response);
//            根据id,处理不同的联网请求
            switch (id) {
                case 100:
                    isLoadMore =true;
//                    保存数据
//                    CacheUtils.putString(mContext, url, response);
//                    解析数据,json格式
                    processData(response);
                    listview.onRefreshFinish(false);
                    break;
            }
        }
    }
    private class MyOnPageChangeListener implements ViewPager.OnPageChangeListener {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            //放这里为了解决有时出现两个红点
            //把上次的点设置为灰色
            ll_point_group.getChildAt(preSelectPosition).setEnabled(false);
            //把当前的点设置为红色
            ll_point_group.getChildAt(position).setEnabled(true);
            preSelectPosition = position;
        }

        @Override
        public void onPageSelected(int position) {
           /* //把上次的点设置为灰色
            ll_point_group.getChildAt(preSelectPosition).setEnabled(false);
            //把当前的点设置为红色
            ll_point_group.getChildAt(position).setEnabled(true);
            preSelectPosition = position;*/

            //改变标题
            tv_title.setText(topnews.get(position).getTitle());
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            switch (state){
                case ViewPager.SCROLL_STATE_DRAGGING:
                    Log.e("TAG", "SCROLL_STATE_DRAGGING --移除消息了了");
                    handler.removeCallbacksAndMessages(null);//移除所有的消息和回调
                    isDragging = true;
                    break;
                case ViewPager.SCROLL_STATE_IDLE:
                    isDragging = false;
                    Log.e("TAG", "SCROLL_STATE_IDLE --发消息");
                    handler.removeCallbacksAndMessages(null);//移除所有的消息和回调
                    handler.postDelayed(new MyRunnable(),4000);
                    break;
                case ViewPager.SCROLL_STATE_SETTLING:
                    if(isDragging){
                        isDragging = false;
                        Log.e("TAG", "SCROLL_STATE_SETTLING --发消息");
                        handler.removeCallbacksAndMessages(null);//移除所有的消息和回调
                        handler.postDelayed(new MyRunnable(),4000);
                    }
                    break;
            }

        }
    }

    @Override
    public void initData() {
        super.initData();
        /*textView.setText(childrenData.getTitle());*/
        Log.d("TabDetailPager", Constants.BASE_URL + childrenData.getUrl());
        url = Constants.BASE_URL + childrenData.getUrl();
        String savejson = CacheUtils.getString(mContext, url);
        if (!TextUtils.isEmpty(savejson)) {
            processData(savejson);
        }
        getDataFromNet();
    }

    private void processData(String savejson) {
        TabDetailPagerBean bean = parsedJson(savejson);
        Log.d("TabDetailPager", bean.getData().getNews().get(2).getTitle());

        String more = bean.getData().getMore();
        if(TextUtils.isEmpty(more)){
            moreurl = "";
        }else{
            moreurl = Constants.BASE_URL+more;
        }
        if(!isLoadMore){
            //1.设置ViewPager的数据
            //得到顶部的数据
            topnews = bean.getData().getTopnews();
            viewpage.setAdapter(new TabDetailPagerAdapter());
            addPoint();
//        设置listview的适配器
            news = bean.getData().getNews();
            adapter = new TabDetailPagerListAdapter();
            listview.setAdapter(adapter);

        }else {
            news.addAll(bean.getData().getNews());
            //刷新适配器
            adapter.notifyDataSetChanged();
            //加载更多
            isLoadMore = false;
        }
        //每隔4秒图片循环一次
        if(handler==null)
        {
            handler = new InteracHandler();
        }
        handler.removeCallbacksAndMessages(null);////移除所有的消息和回调
        handler.postDelayed(new MyRunnable(),4000);
    }
    class InteracHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int item = (viewpage.getCurrentItem()+1) % topnews.size();
            viewpage.setCurrentItem(item);
            handler.postDelayed(new MyRunnable(), 4000);
        }
    }
    class MyRunnable implements Runnable{

        @Override
        public void run() {
            handler.sendEmptyMessage(0);
        }
    }
    class TabDetailPagerListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return news.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = View.inflate(mContext, R.layout.item_tab_detail_pager, null);
                viewHolder = new ViewHolder(convertView);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();

            }
//            根据位置赋值
            TabDetailPagerBean.DataBean.NewsBean newsBean = news.get(position);
            //            获取图片地址
            String imageUrl = Constants.BASE_URL + newsBean.getListimage();
            Glide.with(mContext)
                    .load(imageUrl)
                    .placeholder(R.drawable.news_pic_default)
                    .error(R.drawable.news_pic_default)
                    .into(viewHolder.iv_icon);
            viewHolder.tv_title.setText(newsBean.getTitle());
            viewHolder.tv_time.setText(newsBean.getPubdate());
//            判断是否点击过
            String readArrayId = CacheUtils.getString(mContext, READ_ARRAY_ID);
            if(readArrayId.contains(newsBean.getId()+"")){
                viewHolder.tv_title.setTextColor(Color.GRAY);
            }else{
                viewHolder.tv_title.setTextColor(Color.BLACK);
            }
            return convertView;
        }

    }

    static class ViewHolder {
        @BindView(R.id.iv_icon)
        ImageView iv_icon;
        @BindView(R.id.tv_title)
        TextView tv_title;
        @BindView(R.id.tv_time)
        TextView tv_time;

        ViewHolder(View view) {
            ButterKnife.bind(this, view);
        }
    }

    private void addPoint() {
        //2.根据有多少个页面设置多少个点
//        加载之前清空所有点
        ll_point_group.removeAllViews();

        for (int i = 0; i < topnews.size(); i++) {
            ImageView point = new ImageView(mContext);
            point.setImageResource(R.drawable.point_selector);


            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(DensityUtil.dip2px(mContext, 5), DensityUtil.dip2px(mContext, 5));
            if (i != 0) {
                params.leftMargin = DensityUtil.dip2px(mContext, 10);
            }
            if (i == 0) {
                point.setEnabled(true);
            } else {
                point.setEnabled(false);
            }
            point.setLayoutParams(params);
            //添加导航点
            ll_point_group.addView(point);
        }
        //默认标题
        tv_title.setText(topnews.get(preSelectPosition).getTitle());
    }

    class TabDetailPagerAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return topnews.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            ImageView imageView = new ImageView(mContext);
            imageView.setImageResource(R.drawable.home_scroll_default);

//            获取图片地址
            String imageUrl = Constants.BASE_URL + topnews.get(position).getTopimage();
            Glide.with(mContext)
                    .load(imageUrl)
                    .placeholder(R.drawable.home_scroll_default)
                    .error(R.drawable.home_scroll_default)
                    .into(imageView);
//            加入到ViewPage容器中去
            container.addView(imageView);

            imageView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event)
                {
                    switch (event.getAction()){
                        case MotionEvent.ACTION_DOWN:
                            //移除所有的消息和回调
                            Log.e("TAG", "ACTION_DOWN --移除消息了了");
                            //移除所有的消息和回调
                            handler.removeCallbacksAndMessages(null);
                            break;
                        case MotionEvent.ACTION_MOVE:
                            break;
                        case MotionEvent.ACTION_UP:
                            Log.e("TAG", "ACTION_UP --开始发消息了");
                            //移除所有的消息和回调
                            handler.removeCallbacksAndMessages(null);
                            handler.postDelayed(new MyRunnable(),4000);
                            break;
/*                        case MotionEvent.ACTION_CANCEL://事件丢失
                            Log.e("TAG", "ACTION_CANCEL --开始发消息了");
                            //移除所有的消息和回调
                            handler.removeCallbacksAndMessages(null);
                            handler.postDelayed(new MyRunnable(),4000);
                            break;*/
                    }
                    return true;
//                    设置点击事件要返回false
//                    return false;
                }
            });

            Log.d("TabDetailPagerAdapter", imageUrl);
            return imageView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            //super.destroyItem(container, position, object);
            container.removeView((View) object);
        }
    }

    private TabDetailPagerBean parsedJson(String savejson) {
        return JSON.parseObject(savejson, TabDetailPagerBean.class);
    }

    private void getDataFromNet() {
        OkHttpUtils
                .get()
                .url(url)
                .id(100)
                .build()
                .execute(new MyStringCallback());
    }

    class MyStringCallback extends StringCallback {
        @Override
        public void onBefore(Request request, int id) {
            super.onBefore(request, id);
            Log.d("MyStringCallback", "开始联网。。。");
        }

        @Override
        public void onAfter(int id) {
            super.onAfter(id);
            Log.d("MyStringCallback", "结束联网。。成功失败都会执行。");
        }

        @Override
        public void inProgress(float progress, long total, int id) {
            super.inProgress(progress, total, id);
            Log.d("MyStringCallback", "联网中。。。");
        }

        @Override
        public void onError(Call call, Exception e, int i) {
            e.printStackTrace();
            listview.onRefreshFinish(false);
            Log.d("MyStringCallback", "联网失败:" + e.getMessage());
        }

        @Override
        public void onResponse(String response, int id) {
            listview.onRefreshFinish(true);
            Log.d("MyStringCallback", "联网成功。。。" + response);
//            根据id,处理不同的联网请求
            switch (id) {
                case 100:
//                    保存数据
                    CacheUtils.putString(mContext, url, response);
//                    解析数据,json格式
                    processData(response);
                    break;
            }
        }
    }
}

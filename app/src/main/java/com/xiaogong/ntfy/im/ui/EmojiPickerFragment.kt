package com.xiaogong.ntfy.im.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.xiaogong.ntfy.im.R

class EmojiPickerFragment : BottomSheetDialogFragment() {

    var onEmojiSelected: ((String) -> Unit)? = null

    data class EmojiCategory(val name: String, val emoji: List<String>)

    private val categories = listOf(
        EmojiCategory("😀 表情", listOf(
            "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃",
            "😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙",
            "😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔",
            "😐","😑","😶","😏","😒","🙄","😬","🤥","😌","😔",
            "😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🥵","🥶",
            "🥴","😵","🤯","🤠","🥳","😎","🤓","🧐","😕","😟",
            "🙁","☹️","😮","😯","😲","😳","🥺","😦","😧","😨"
        )),
        EmojiCategory("👋 手势", listOf(
            "👍","👎","👏","🙌","🤝","🤜","🤛","✊","👊","🤚",
            "✋","🖐️","👋","🤙","💪","☝️","👆","👇","👉","👈",
            "🤞","🤟","🤘","👌","🤌","🤏","👐","🤲","🙏","✍️",
            "💅","🤳","💃","🕺","🧍","🧎","🧏","🤦","🤷","🙋"
        )),
        EmojiCategory("❤️ 心形", listOf(
            "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔",
            "❤️‍🔥","❤️‍🩹","💖","💗","💓","💞","💕","💟","❣️","💝",
            "💘","💌","💋","😻","💑","👫","👬","👭","🫂","💏"
        )),
        EmojiCategory("🐶 动物", listOf(
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯",
            "🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐔","🐧",
            "🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄",
            "🐝","🐛","🦋","🐌","🐞","🐜","🦟","🦗","🐢","🐍"
        )),
        EmojiCategory("🌸 自然", listOf(
            "🌸","🌺","🌻","🌹","🥀","🌷","🌱","🌿","☘️","🍀",
            "🎋","🍃","🍂","🍁","🌾","🌵","🌲","🌳","🌴","🪨",
            "🌊","🌬️","🌀","🌈","⭐","🌟","💫","✨","☀️","🌤️",
            "⛅","🌥️","☁️","🌦️","🌧️","⛈️","🌩️","🌨️","❄️","☃️"
        )),
        EmojiCategory("🍕 食物", listOf(
            "🍎","🍊","🍋","🍇","🍓","🍒","🍑","🥭","🍍","🥝",
            "🍕","🍔","🍟","🌮","🌯","🥪","🍜","🍣","🍱","🍛",
            "🍗","🍖","🥩","🥚","🍳","🥞","🧇","🥓","🥐","🍞",
            "☕","🍵","🧋","🥤","🍺","🍻","🎂","🍰","🧁","🍫"
        )),
        EmojiCategory("🎉 庆祝", listOf(
            "🎉","🎊","🎈","🎁","🎀","🎗️","🎟️","🎫","🏆","🥇",
            "🥈","🥉","🎯","🎮","🎲","🃏","🎴","🎭","🎨","🎬",
            "🎤","🎧","🎷","🎸","🎹","🎺","🎻","🥁","🎵","🎶"
        )),
        EmojiCategory("✅ 符号", listOf(
            "✅","❌","❓","❗","💯","🔥","⚡","💥","🌀","🎯",
            "♻️","🔔","🔕","📣","📢","💬","💭","🗨️","🗯️","📌",
            "📍","🔑","🗝️","🔒","🔓","🔨","⚙️","🔧","🔩","⚒️",
            "🆒","🆕","🆙","🆓","🆗","🆘","⛔","🚫","✔️","➕"
        ))
    )

    private lateinit var emojiAdapter: EmojiAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_emoji_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val grid = view.findViewById<RecyclerView>(R.id.emoji_picker_grid)
        val chipGroup = view.findViewById<ChipGroup>(R.id.emoji_picker_category_chips)

        emojiAdapter = EmojiAdapter { emoji ->
            onEmojiSelected?.invoke(emoji)
        }
        grid.layoutManager = GridLayoutManager(requireContext(), 6)
        grid.adapter = emojiAdapter
        emojiAdapter.submitList(categories[0].emoji)

        categories.forEachIndexed { index, category ->
            val chip = Chip(requireContext()).apply {
                text = category.name
                isCheckable = true
                isChecked = (index == 0)
            }
            chip.setOnClickListener {
                emojiAdapter.submitList(categories[index].emoji)
                grid.scrollToPosition(0)
            }
            chipGroup.addView(chip)
        }
    }

    private class EmojiAdapter(
        private val onPick: (String) -> Unit
    ) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

        private var items: List<String> = emptyList()

        fun submitList(list: List<String>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
            val density = parent.context.resources.displayMetrics.density
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                textSize = 26f
                gravity = Gravity.CENTER
                minHeight = (48 * density).toInt()
                isClickable = true
                isFocusable = true
                val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
                val ta = parent.context.obtainStyledAttributes(attrs)
                background = ta.getDrawable(0)
                ta.recycle()
            }
            return EmojiViewHolder(tv)
        }

        override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
            holder.tv.text = items[position]
            holder.tv.setOnClickListener { onPick(items[position]) }
        }

        override fun getItemCount() = items.size

        class EmojiViewHolder(val tv: TextView) : RecyclerView.ViewHolder(tv)
    }

    companion object {
        const val TAG = "EmojiPickerFragment"
    }
}

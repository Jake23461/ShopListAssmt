package com.mmgpt.shoplistgpt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.mmgpt.shoplistgpt.adapter.ItemAdapter
import com.mmgpt.shoplistgpt.databinding.FragmentCategoryBinding
import com.mmgpt.shoplistgpt.ShoppingItem

class PlanCategoryFragment : Fragment() {

    private val viewModel: ShoppingListViewModel by activityViewModels()
    private var index: Int = 0
    private var _binding: FragmentCategoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        index = arguments?.getInt(ARG_INDEX) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.chkSelectAll.visibility = View.VISIBLE
        lateinit var adapter: ItemAdapter
        val dragHelper = reorderDragHelper({ adapter }) { from, to ->
            viewModel.reorderItem(index, from, to)
        }

        adapter = ItemAdapter(
            onAction = { item, action ->
                when (action) {
                    ItemAdapter.Action.CHECK -> viewModel.checkItem(item)
                    ItemAdapter.Action.EDIT -> showEditDialog(item.name) { viewModel.editItem(item, it) }
                    ItemAdapter.Action.DELETE -> viewModel.deleteItem(item)
                    else -> {}
                }
            },
            mode = ItemAdapter.Mode.PLAN,
            dragStart = { vh -> dragHelper.startDrag(vh) },
            onAdd = { position, name ->
                viewModel.addItem(index, position - 1, name)
            }
        )
        binding.recyclerView.adapter = adapter
        dragHelper.attachToRecyclerView(binding.recyclerView)

        viewModel.categories.observe(viewLifecycleOwner) { list ->
            val cat = list.getOrNull(index)
            if (cat != null) {
                binding.tvHeading.text = cat.heading
                binding.tvCount.text = ""

                val allChecked = cat.items.filterNot { it.isHeading }.all { it.isChecked }
                binding.chkSelectAll.setOnCheckedChangeListener(null)
                binding.chkSelectAll.isChecked = allChecked
                binding.chkSelectAll.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.setAllChecked(index, isChecked)
                }

                val items = mutableListOf<ShoppingItem>()
                items.add(ShoppingItem(-1, "", isPlaceholder = true))
                items.addAll(cat.items)
                items.add(ShoppingItem(-2, "", isPlaceholder = true))
                adapter.submitList(items)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_INDEX = "index"
        fun newInstance(index: Int): PlanCategoryFragment {
            val fragment = PlanCategoryFragment()
            fragment.arguments = Bundle().apply { putInt(ARG_INDEX, index) }
            return fragment
        }
    }
}
